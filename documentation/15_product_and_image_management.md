# 15 — Product & Image Management

Admin screens to create and manage products with variants, attributes and
multiple images. Designed so the customer side can render everything (images,
colours, sizes, variants, pricing, availability) from a clean data model.

---

## 15.1 Data model

```
categories 1───* products 1───* product_variants   (colour/size, own SKU, stock, price)
                      │
                      ├───* product_images         (URL only; is_primary, display_order; optional variant_id)
                      └───* product_attributes      (free-form name/value specs)
```

- **products** — core details + `product_type` and `brand` (added columns).
- **product_variants** — each purchasable colour/size combo: own unique `sku`,
  optional `price`/`discount_price` (NULL = inherit product price), `stock_quantity`,
  `status`. This is the scalable way to model colours/sizes/variants.
- **product_images** — one row per image; stores only the **URL/path**, an
  `is_primary` flag, a `display_order`, and an optional `variant_id` (so a colour can
  have its own photos).
- **product_attributes** — arbitrary specs (Material=Cotton, Weight=200g).

SQL to create all of this: **`db/2_product_management.sql`** (run once against
`ecommerce_db`). It also seeds a few categories.

Why this shape: the product aggregate loads with its images/variants/attributes in
one query (`ProductRepository.findWithDetailsById` via `@EntityGraph`), and the
customer page can show price (`getEffectivePrice()`), the primary image
(`getPrimaryImage()`), the colour/size options (variants) and stock
(`variant.isInStock()`) with no extra joins in code.

> The entity collections are `Set` (with `@OrderBy`) rather than `List` on purpose:
> fetching several `List` collections in one `@EntityGraph` throws Hibernate's
> `MultipleBagFetchException`. `Set` avoids that.

## 15.2 Image storage (no session, only URL in DB)

`service/ImageStorageService` writes uploaded files to a local folder and returns a
public URL; **only that URL is stored** in `product_images.image_url`.

- Upload dir: `app.upload.dir` (default `uploads/`), files under `uploads/products/`.
- Returned URL: `/uploads/products/<uuid>.<ext>`.
- `WebConfig` maps `/uploads/**` → that folder, and `/uploads/**` is public in
  `SecurityConfig`, so admin and customers can render images by URL.
- Multipart limits: `spring.servlet.multipart.max-file-size=5MB`, request `25MB`.
- Allowed types: jpg/jpeg/png/gif/webp; filenames are randomised (UUID) to avoid
  collisions and unsafe names.

**Swapping to S3/Azure for production:** replace the body of `ImageStorageService`
(`store`/`delete`) with an SDK call that uploads to the bucket/container and returns
the object's URL. Nothing else changes — controllers and DB stay the same because we
persist only the URL. That's the whole point of isolating storage behind this service.

## 15.3 Admin screens (Thymeleaf)

Left-menu item **Products** → `AdminController`-style routes in
`AdminProductController` (all under `/admin/**`, ROLE_ADMIN):

| Route | Purpose |
|-------|---------|
| `GET /admin/products` | List (thumbnail, name, SKU, category, price, status) + quick "add category" |
| `GET /admin/products/new` → `POST /admin/products` | Create core details |
| `GET /admin/products/{id}` | Edit page: details + images + variants + delete |
| `POST /admin/products/{id}` | Save core details |
| `POST /admin/products/{id}/images` | Upload one or more images (first becomes primary) |
| `POST /admin/products/{id}/images/{imgId}/primary` | Choose the primary image |
| `POST /admin/products/{id}/images/{imgId}/delete` | Remove an image (file + row) |
| `POST /admin/products/{id}/variants` | Add a colour/size variant |
| `POST /admin/products/{id}/variants/{vid}/delete` | Remove a variant |
| `POST /admin/products/{id}/delete` | Delete the product (and its image files) |

Templates: `admin/products.html`, `admin/product-form.html` (create),
`admin/product-edit.html` (manage everything). The image upload form uses
`enctype="multipart/form-data"`; variants and attributes are simple sub-forms so no
complex nested binding is needed.

Flow: create the product's core details → you're taken to the edit page → upload
images and add variants there. Attributes are entered as `name: value` lines.

## 15.4 How the customer side would consume this

Given a `Product` loaded with `findWithDetailsById`:

- **Gallery:** iterate `product.images` (ordered); lead with `getPrimaryImage()`.
- **Price:** show `getEffectivePrice()`; if `discountPrice != null`, also show the
  struck-through `price`.
- **Colours/sizes:** derive from `product.variants` (distinct colours, sizes per
  colour). Each variant has its own `sku`, price override and `stockQuantity`.
- **Availability:** `variant.isInStock()` (stock > 0 and ACTIVE); product-level
  `status` gates the whole item.

No customer controller is included yet — this task is the admin/management side —
but the model is shaped so a product page is a straightforward read of one aggregate.

## 15.5 Run / test

1. Apply `db/2_product_management.sql`.
2. Restart the app; log in as admin.
3. **Products → New product** → fill details → create.
4. On the edit page: **Upload images** (first is auto-primary; change with "Set
   primary"), then **Add variant** rows for colours/sizes.
5. Check `uploads/products/` on disk — the files are there; the DB holds only URLs.
