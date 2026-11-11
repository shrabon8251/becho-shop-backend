package com.becho.saas.modules.catalog.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Manual collection membership: the {@code collection_products} join table
 * (created in V2__catalog.sql, PK = collection_id + product_id, maintained
 * with plain JDBC in StoreCollectionController). There was no JPA entity for
 * it until now — which is why the Products page could not filter by
 * collection. Read-only use: writes still go through StoreCollectionController.
 */
@Entity
@Table(name = "collection_products")
@IdClass(CollectionProduct.CollectionProductId.class)
public class CollectionProduct {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "position", nullable = false)
    private int position;

    public Collection getCollection() { return collection; }
    public Product getProduct() { return product; }
    public int getPosition() { return position; }

    /** Composite key — field names/types must match the @Id properties above. */
    public static class CollectionProductId implements Serializable {
        private Long collection;
        private Long product;

        public CollectionProductId() {}
        public CollectionProductId(Long collection, Long product) {
            this.collection = collection;
            this.product = product;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CollectionProductId that)) return false;
            return Objects.equals(collection, that.collection) && Objects.equals(product, that.product);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collection, product);
        }
    }
}
