package com.example.model;

import com.example.model.order.ProcessedOrder;

import java.util.Collection;

public sealed interface Processed {
    record Correct(Collection<ProcessedOrder.Bought> bought, Collection<ProcessedOrder.Sold> sold,
                   Collection<ProcessedOrder.Expired> expired) implements Processed {
    }

    record Failed(String message) implements Processed {
    }
}
