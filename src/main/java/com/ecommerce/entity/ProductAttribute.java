package com.ecommerce.entity;

import jakarta.persistence.*;

/**
 * A free-form key/value spec for a product (e.g. Material=Cotton, Weight=200g).
 * Backs the {@code product_attributes} table.
 */
@Entity
@Table(name = "product_attributes")
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "attr_name", nullable = false, length = 100)
    private String name;

    @Column(name = "attr_value", nullable = false, length = 500)
    private String value;

    public ProductAttribute() {}

    public ProductAttribute(Product product, String name, String value) {
        this.product = product;
        this.name = name;
        this.value = value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
