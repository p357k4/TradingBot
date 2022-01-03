package com.example.model;

import com.example.model.order.Instrument;
import com.example.model.order.SubmittedOrder;

import java.util.Collection;

public sealed interface Portfolio {
    record Current(Collection<PortfolioElement> portfolio, Collection<SubmittedOrder.Buy> toBuy,
                   Collection<SubmittedOrder.Sell> toSell,
                   Long cash) implements Portfolio {
    }

    record Failed(String message) implements Portfolio {
    }

    record PortfolioElement(Instrument instrument, long qty) {
    }
}
