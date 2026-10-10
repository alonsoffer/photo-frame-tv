package com.photoframe.tv.drive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

interface DriveRepository {
    /** All images inside [folderId] (null == root), optionally recursing into subfolders. */
    suspend fun listPhotos(folderId: String?, includeSubfolders: Boolean): List<Photo>

    /** Immediate subfolders of [parentId] (null == root), for the folder browser. */
    suspend fun listFolders(parentId: String?): List<DriveFolder>

    /** Upload an image into the app's dedicated Drive folder. Only meaningful for the Drive source. */
    suspend fun uploadPhoto(name: String, mimeType: String, bytes: ByteArray) {}

    /** Delete a photo by id from Drive. Only meaningful for the Drive source. */
    suspend fun deletePhoto(id: String) {}
}

/**
 * Talks to the real Google Drive REST API v3 over the authenticated OkHttp client
 * (the [com.photoframe.tv.auth.AuthInterceptor] adds the Bearer token).
 */
class GoogleDriveRepository(
    private val client: OkHttpClient,
    private val json: Json,
) : DriveRepository {

    override suspend fun listPhotos(folderId: String?, includeSubfolders: Boolean): List<Photo> =
        withContext(Dispatchers.IO) {
            // drive.file: the app manages its own dedicated folder ("Photo Frame") in the Drive root.
            // The phone uploads photos into it; here we just list the images inside it.
            val folder = findAppFolder() ?: return@withContext emptyList()
            val photos = mutableListOf<Photo>()
            forEachPage("'$folder' in parents and mimeType contains 'image/' and trashed = false") { file ->
                photos += Photo(
                    id = file.id,
                    name = file.name,
                    url = mediaUrl(file.id),
                    thumbnailLink = file.thumbnailLink,
                )
            }
            photos
        }

    /** The app's dedicated "Photo Frame" folder in the Drive root, or null if it doesn't exist yet. */
    private fun findAppFolder(): String? {
        var id: String? = null
        forEachPage(
            "mimeType = 'application/vnd.google-apps.folder' and name = '$APP_FOLDER' and " +
                "'root' in parents and trashed = false",
        ) { file -> if (id == null) id = file.id }
        return id
    }

    /** Find the app folder, creating it in the Drive root if it doesn't exist. For the uploader side. */
    suspend fun findOrCreateAppFolder(): String = withContext(Dispatchers.IO) {
        findAppFolder() ?: run {
            val body = """{"name":"$APP_FOLDER","mimeType":"application/vnd.google-apps.folder","parents":["root"]}"""
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("https://www.googleapis.com/drive/v3/files?fields=id").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("create folder ${resp.code}: ${text.take(200)}")
                json.decodeFromString(IdDto.serializer(), text).id
            }
        }
    }

    /** Upload one image into the app's "Photo Frame" folder (multipart create). */
    override suspend fun uploadPhoto(name: String, mimeType: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val folder = findOrCreateAppFolder()
            val boundary = "pfUpload" + System.nanoTime()
            val safeName = name.replace("\\", "\\\\").replace("\"", "\\\"")
            val meta = "{\"name\":\"$safeName\",\"parents\":[\"$folder\"]}"
            val out = java.io.ByteArrayOutputStream()
            out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
            out.write("--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n--$boundary--".toByteArray())
            val body = out.toByteArray().toRequestBody("multipart/related; boundary=$boundary".toMediaType())
            val req = Request.Builder().url(UPLOAD_URL).post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("upload ${resp.code}: ${resp.body?.string()?.take(200)}")
            }
        }
    }

    override suspend fun deletePhoto(id: String) {
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id").delete().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    throw IOException("delete ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
            }
        }
    }

    override suspend fun listFolders(parentId: String?): List<DriveFolder> =
        withContext(Dispatchers.IO) {
            val parent = parentId ?: "root"
            val folders = mutableListOf<DriveFolder>()
            forEachPage("'$parent' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false") { file ->
                folders += DriveFolder(id = file.id, name = file.name)
            }
            folders
        }

    private fun forEachPage(query: String, onFile: (DriveFileDto) -> Unit) {
        var pageToken: String? = null
        do {
            val page = requestPage(query, pageToken)
            page.files.forEach(onFile)
            pageToken = page.nextPageToken
        } while (pageToken != null)
    }

    private fun requestPage(query: String, pageToken: String?): FileListDto {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("drive/v3/files")
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "nextPageToken, files(id,name,mimeType,thumbnailLink)")
            .addQueryParameter("pageSize", "200")
            .addQueryParameter("orderBy", "folder,name_natural")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("includeItemsFromAllDrives", "true")
            .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
            .build()

        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Drive API ${resp.code}: ${body.take(200)}")
            return json.decodeFromString(FileListDto.serializer(), body)
        }
    }

    private fun mediaUrl(id: String) = "https://www.googleapis.com/drive/v3/files/$id?alt=media"

    private companion object {
        const val APP_FOLDER = "Photo Frame"
    }
}

/**
 * Reads photos from an on-device (or USB) folder the user picked via the Storage Access Framework.
 * [folderId] is the persisted SAF *tree* URI; images load through their `content://` document URIs
 * (no storage permission needed — access rides on the persisted URI grant). Enumeration uses
 * DocumentsContract directly (one query per folder) rather than the slower DocumentFile wrapper.
 */
class LocalPhotoRepository(private val context: Context) : DriveRepository {

    override suspend fun listPhotos(folderId: String?, includeSubfolders: Boolean): List<Photo> =
        withContext(Dispatchers.IO) {
            val treeUri = folderId?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: return@withContext emptyList()
            val photos = mutableListOf<Photo>()
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val toVisit = ArrayDeque<String>().apply { add(rootDocId) }
            var safety = 0

            while (toVisit.isNotEmpty() && safety < MAX_FOLDERS) {
                val docId = toVisit.removeFirst()
                safety++
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (includeSubfolders) toVisit.add(id)
                        } else if (mime.startsWith("image/")) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
                            photos += Photo(id = docUri, name = name, url = docUri)
                        }
                    }
                }
            }
            photos.sortedBy { it.name.lowercase() }
        }

    // Local folders are picked with the system SAF picker, so no in-app folder browser is needed.
    override suspend fun listFolders(parentId: String?): List<DriveFolder> = emptyList()

    private companion object {
        const val MAX_FOLDERS = 2000
    }
}

private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

/**
 * Demo repository used when no OAuth credentials are configured. Serves public sample
 * images so the slideshow UI can be exercised end-to-end without signing in.
 */
class FakeDriveRepository : DriveRepository {
    private val samples = (1..12).map { i ->
        Photo(
            id = "demo-$i",
            name = "Sample $i",
            url = "https://picsum.photos/seed/frame$i/1920/1080",
        )
    }

    override suspend fun listPhotos(folderId: String?, includeSubfolders: Boolean): List<Photo> = samples

    override suspend fun listFolders(parentId: String?): List<DriveFolder> = when (parentId) {
        null -> listOf(
            DriveFolder("demo-family", "Family Photos"),
            DriveFolder("demo-trips", "Trips"),
        )
        else -> emptyList()
    }
}
