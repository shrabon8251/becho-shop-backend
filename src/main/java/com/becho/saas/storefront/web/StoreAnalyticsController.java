package com.becho.saas.storefront.web;

import com.becho.saas.modules.analytics.repository.StoreVisitRepository;
import com.becho.saas.modules.orders.repository.OrderRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merchant analytics for the admin dashboard: GET /stores/{storeId}/analytics?range=7|30|90.
 *
 * <p>Conversion rate = converted orders / unique visitors in the range.
 * A "converted" order is one whose financial_status is paid or refunded
 * (it was a real purchase, even if later refunded). Visitors are unique
 * per day from the store_visits table. The previous equal-length period is
 * returned too, so the dashboard can show a delta.
 */
@RestController
@RequestMapping("/stores/{storeId}/analytics")
public class StoreAnalyticsController {

    private final StoreScope scope;
    private final StoreVisitRepository visits;
    private final OrderRepository orders;

    public StoreAnalyticsController(StoreScope scope, StoreVisitRepository visits, OrderRepository orders) {
        this.scope = scope;
        this.visits = visits;
        this.orders = orders;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public Map<String, Object> get(@PathVariable String storeId,
                                   @RequestParam(required = false, defaultValue = "7") int range) {
        Long sid = scope.require(storeId);
        int days = (range == 30 || range == 90) ? range : 7;
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1);
        LocalDate prevFrom = from.minusDays(days);
        LocalDate prevTo = from.minusDays(1);

        long visitors = visits.countVisitors(sid, from, today);
        long paidOrders = orders.countConvertedOrders(sid,
                from.atStartOfDay(), LocalDateTime.now());
        long prevVisitors = visits.countVisitors(sid, prevFrom, prevTo);
        long prevPaidOrders = orders.countConvertedOrders(sid,
                prevFrom.atStartOfDay(), from.atStartOfDay());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rangeDays", days);
        m.put("visitors", visitors);
        m.put("paidOrders", paidOrders);
        m.put("conversionRate", rate(paidOrders, visitors));
        m.put("prevVisitors", prevVisitors);
        m.put("prevPaidOrders", prevPaidOrders);
        m.put("prevConversionRate", rate(prevPaidOrders, prevVisitors));
        return m;
    }

    /** Percent, 2dp rounding via the frontend's toFixed; null when no visitors yet. */
    private static Double rate(long orders, long visitors) {
        if (visitors <= 0) return null;
        return orders * 100.0 / visitors;
    }
}
