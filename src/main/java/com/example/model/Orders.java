package com.example.model;

import com.example.model.order.SubmittedOrder;

import java.util.Collection;

public sealed interface Orders {
    record Correct(Collection<SubmittedOrder.Buy> buy, Collection<SubmittedOrder.Sell> sell) implements Orders {
    }

    record Failed(String message) implements Orders {
    }
}
