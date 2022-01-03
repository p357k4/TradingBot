package com.example.model;

public sealed interface SubmitOrder {
    record Sell(String symbol, String tradeId, long qty, long ask) implements SubmitOrder {}
    record Buy(String symbol, String tradeId, long qty, long bid) implements SubmitOrder {}
}
