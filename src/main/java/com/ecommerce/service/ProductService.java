package com.ecommerce.service;

import com.ecommerce.entity.*;
import com.ecommerce.exception.AuthException;
import com.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * All product-management logic: product CRUD plus managing its variants,
 * images and attributes. Image bytes go through {@link ImageStorageService};
 * only the returned URL is persisted.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final ImageStorageService imageStorage;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductVariantRepository variantRepository,
                          ProductImageRepository imageRepository,
                          ImageStorageService imageStorage) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
    }

    // ---------- Product CRUD ----------

    @Transactional(readOnly = true)
    public List<Product> list() {
        return productRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Product getDetails(Long id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AuthException("Product not found: " + id));
    }

    @Transactional
    public Product create(String name, String sku, Long categoryId, String type, String brand,
                          String description, String price, String discountPrice, String status,
                          String attributesText) {
        if (productRepository.existsBySku(sku.trim())) {
            throw new AuthException("A product with SKU '" + sku + "' already exists.");
        }
        Product p = new Product();
        apply(p, name, sku, categoryId, type, brand, description, price, discountPrice, status);
        productRepository.save(p);
        replaceAttributes(p, attributesText);
        return p;
    }

    @Transactional
    public void update(Long id, String name, String sku, Long categoryId, String type, String brand,
                       String description, String price, String discountPrice, String status,
                       String attributesText) {
        Product p = getDetails(id);
        if (!p.getSku().equals(sku.trim()) && productRepository.existsBySku(sku.trim())) {
            throw new AuthException("Another product already uses SKU '" + sku + "'.");
        }
        apply(p, name, sku, categoryId, type, brand, description, price, discountPrice, status);
        replaceAttributes(p, attributesText);
        productRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        Product p = getDetails(id);
        p.getImages().forEach(img -> imageStorage.delete(img.getImageUrl())); // clean files too
        productRepository.delete(p);
    }

    // ---------- Variants ----------

    @Transactional
    public void addVariant(Long productId, String sku, String color, String size,
                           String price, String discountPrice, int stock, String status) {
        Product p = getDetails(productId);
        if (variantRepository.existsBySku(sku.trim())) {
            throw new AuthException("Variant SKU '" + sku + "' already exists.");
        }
        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setSku(sku.trim());
        v.setColor(blankToNull(color));
        v.setSize(blankToNull(size));
        v.setPrice(money(price));
        v.setDiscountPrice(money(discountPrice));
        v.setStockQuantity(Math.max(0, stock));
        v.setStatus(status == null ? "ACTIVE" : status);
        p.getVariants().add(v);
        productRepository.save(p);
    }

    @Transactional
    public void deleteVariant(Long variantId) {
        variantRepository.deleteById(variantId);
    }

    // ---------- Images ----------

    @Transactional
    public void addImages(Long productId, MultipartFile[] files) {
        Product p = getDetails(productId);
        boolean hasPrimary = p.getImages().stream().anyMatch(ProductImage::isPrimary);
        int order = p.getImages().size();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String url = imageStorage.store(file);
            ProductImage img = new ProductImage();
            img.setProduct(p);
            img.setImageUrl(url);
            img.setDisplayOrder(order++);
            if (!hasPrimary) {           // first ever image becomes primary
                img.setPrimary(true);
                hasPrimary = true;
            }
            p.getImages().add(img);
        }
        productRepository.save(p);
    }

    @Transactional
    public void setPrimaryImage(Long productId, Long imageId) {
        Product p = getDetails(productId);
        p.getImages().forEach(img -> img.setPrimary(img.getId().equals(imageId)));
        productRepository.save(p);
    }

    @Transactional
    public void deleteImage(Long imageId) {
        ProductImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new AuthException("Image not found: " + imageId));
        imageStorage.delete(img.getImageUrl());
        imageRepository.delete(img);
    }

    // ---------- helpers ----------

    private void apply(Product p, String name, String sku, Long categoryId, String type, String brand,
                       String description, String price, String discountPrice, String status) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AuthException("Select a valid category."));
        p.setName(name.trim());
        p.setSku(sku.trim());
        p.setCategory(category);
        p.setProductType(blankToNull(type));
        p.setBrand(blankToNull(brand));
        p.setDescription(description);
        p.setPrice(requireMoney(price));
        p.setDiscountPrice(money(discountPrice));
        p.setStatus(status == null ? "ACTIVE" : status);
    }

    /** Rebuilds the attribute list from "name: value" lines. */
    private void replaceAttributes(Product p, String text) {
        p.getAttributes().clear();
        if (text != null) {
            for (String line : text.split("\\r?\\n")) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                int sep = s.indexOf(':') >= 0 ? s.indexOf(':') : s.indexOf('=');
                if (sep <= 0) continue;
                String name = s.substring(0, sep).trim();
                String value = s.substring(sep + 1).trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    p.getAttributes().add(new ProductAttribute(p, name, value));
                }
            }
        }
    }

    private BigDecimal requireMoney(String v) {
        BigDecimal m = money(v);
        if (m == null) throw new AuthException("Price is required and must be a number.");
        if (m.signum() < 0) throw new AuthException("Price cannot be negative.");
        return m;
    }

    private BigDecimal money(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw new AuthException("Invalid number: " + v);
        }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
