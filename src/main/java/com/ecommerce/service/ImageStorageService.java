package com.ecommerce.service;

import com.ecommerce.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded product images on the local filesystem and returns a public
 * URL/path (e.g. {@code /uploads/products/ab12.jpg}) — that URL is what gets
 * saved in the database, never the bytes and never the HTTP session.
 *
 * <p>This is intentionally behind a small interface-like surface (store/delete)
 * so it can later be swapped for S3 / Azure Blob with no controller changes.</p>
 */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED =
            Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String PUBLIC_PREFIX = "/uploads/products/";

    private final Path productDir;

    public ImageStorageService(@Value("${app.upload.dir:uploads}") String dir) {
        this.productDir = Paths.get(dir).toAbsolutePath().normalize().resolve("products");
        try {
            Files.createDirectories(productDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory: " + productDir, e);
        }
    }

    /** Saves the file and returns the public URL to store in the DB. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AuthException("Please choose an image file.");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new AuthException("Unsupported image type: " + ext);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path target = productDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image", e);
        }
        return PUBLIC_PREFIX + filename;
    }

    /** Best-effort delete of a stored file given its public URL. */
    public void delete(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(productDir.resolve(publicUrl.substring(PUBLIC_PREFIX.length())));
        } catch (IOException ignored) {
            // A missing file on delete is not fatal.
        }
    }

    private String extensionOf(String originalName) {
        String ext = StringUtils.getFilenameExtension(
                originalName == null ? "" : originalName.toLowerCase());
        return ext == null ? "" : ext;
    }
}
