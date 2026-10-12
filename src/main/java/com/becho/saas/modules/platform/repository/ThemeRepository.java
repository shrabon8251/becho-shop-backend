package com.becho.saas.modules.platform.repository;

import com.becho.saas.modules.platform.entity.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ThemeRepository extends JpaRepository<Theme, Long> {
    List<Theme> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    Optional<Theme> findByStoreIdAndPublishedTrue(Long storeId);
    /** All theme rows of a store — one per gallery preset (keyed by presetKey). */
    List<Theme> findByStoreId(Long storeId);
    /** The saved customization of one gallery preset, if the merchant ever saved it. */
    Optional<Theme> findByStoreIdAndPresetKey(Long storeId, String presetKey);
}
