package com.example.model.order;

public sealed interface ValidatedOrder {
    record Acknowledged(Identifier tradeId) implements ValidatedOrder {
    }

    record Rejected(String becauseOf, Identifier tradeId) implements ValidatedOrder {
    }

    record Failed(String message) implements ValidatedOrder {
    }
}
