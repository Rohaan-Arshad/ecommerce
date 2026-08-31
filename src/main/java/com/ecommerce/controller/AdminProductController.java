package com.ecommerce.controller;

import com.ecommerce.exception.AuthException;
import com.ecommerce.service.CategoryService;
import com.ecommerce.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin product management (ROLE_ADMIN via /admin/** in SecurityConfig).
 * Product core details are one form; variants, images and attributes are managed
 * on the edit page through small dedicated POST actions.
 */
@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public AdminProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("active", "products");
        model.addAttribute("products", productService.list());
        model.addAttribute("categories", categoryService.all());
        return "admin/products";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("active", "products");
        model.addAttribute("categories", categoryService.all());
        return "admin/product-form";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam String sku,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String productType,
                         @RequestParam(required = false) String brand,
                         @RequestParam(required = false) String description,
                         @RequestParam String price,
                         @RequestParam(required = false) String discountPrice,
                         @RequestParam(defaultValue = "ACTIVE") String status,
                         @RequestParam(required = false) String attributes,
                         RedirectAttributes ra) {
        try {
            var p = productService.create(name, sku, categoryId, productType, brand,
                    description, price, discountPrice, status, attributes);
            return "redirect:/admin/products/" + p.getId();
        } catch (AuthException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/products/new";
        }
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        var product = productService.getDetails(id);
        String attrText = product.getAttributes().stream()
                .map(a -> a.getName() + ": " + a.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        model.addAttribute("active", "products");
        model.addAttribute("product", product);
        model.addAttribute("attrText", attrText);
        model.addAttribute("categories", categoryService.all());
        return "admin/product-edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam String sku,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String productType,
                         @RequestParam(required = false) String brand,
                         @RequestParam(required = false) String description,
                         @RequestParam String price,
                         @RequestParam(required = false) String discountPrice,
                         @RequestParam(defaultValue = "ACTIVE") String status,
                         @RequestParam(required = false) String attributes,
                         RedirectAttributes ra) {
        try {
            productService.update(id, name, sku, categoryId, productType, brand,
                    description, price, discountPrice, status, attributes);
            ra.addFlashAttribute("ok", "Product saved.");
        } catch (AuthException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        productService.delete(id);
        return "redirect:/admin/products";
    }

    // ---- variants ----
    @PostMapping("/{id}/variants")
    public String addVariant(@PathVariable Long id,
                             @RequestParam String sku,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false) String size,
                             @RequestParam(required = false) String price,
                             @RequestParam(required = false) String discountPrice,
                             @RequestParam(defaultValue = "0") int stock,
                             @RequestParam(defaultValue = "ACTIVE") String status,
                             RedirectAttributes ra) {
        try {
            productService.addVariant(id, sku, color, size, price, discountPrice, stock, status);
        } catch (AuthException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/{id}/variants/{variantId}/delete")
    public String deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        productService.deleteVariant(variantId);
        return "redirect:/admin/products/" + id;
    }

    // ---- images ----
    @PostMapping("/{id}/images")
    public String uploadImages(@PathVariable Long id,
                               @RequestParam("files") MultipartFile[] files,
                               RedirectAttributes ra) {
        try {
            productService.addImages(id, files);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/{id}/images/{imageId}/primary")
    public String setPrimary(@PathVariable Long id, @PathVariable Long imageId) {
        productService.setPrimaryImage(id, imageId);
        return "redirect:/admin/products/" + id;
    }

    @PostMapping("/{id}/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productService.deleteImage(imageId);
        return "redirect:/admin/products/" + id;
    }

    // ---- quick category create (from the products list) ----
    @PostMapping("/categories")
    public String addCategory(@RequestParam String name,
                              @RequestParam(required = false) String description,
                              RedirectAttributes ra) {
        try {
            categoryService.create(name, description);
        } catch (AuthException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/products";
    }
}
