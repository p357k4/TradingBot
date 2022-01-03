package com.example.model;

public sealed interface Info {
    record Get(String symbol) implements Info {};
}
