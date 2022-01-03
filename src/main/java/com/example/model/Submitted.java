package com.example.model;

import com.example.model.order.SubmittedOrder;

import java.util.Collection;

public sealed interface Submitted {
    record Correct(Collection<SubmittedOrder.Buy> buy, Collection<SubmittedOrder.Sell> sell) implements Submitted {
    }

    record Failed(String message) implements Submitted {
    }
}
