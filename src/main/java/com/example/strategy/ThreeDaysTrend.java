package com.example.strategy;

import com.example.Platform;
import com.example.model.rest.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// algorithm making decision on buy/sell based on
// at least three days historical data.
// calculate average price per each of three latest days found in history,
// then check trend:
// if price growing: candidate to buy,
// if price dropping: candidate to sell.
// decide on trade only if data from at least three days available.
// predict next day price by extrapolating from past three days.
public record ThreeDaysTrend(Platform platform) implements Runnable {
    private record DayAverage(LocalDate date, Double average) {
        @Override
        public String toString() {
            return String.format("(date=%s, average=%s)", date, average);
        }
    }
    private record Stats(String instrumentSymbol, Long prediction, Double delta, List<DayAverage> averages) {
    }
    private static final long minQty = 10;
    private static final long minAsk = 13;
    private static final long minBid = 79;
    private static final Logger logger = LoggerFactory.getLogger(ThreeDaysTrend.class);

    @Override
    public void run() {
        final var fetchedInstruments = platform.instruments();
        final var fetchedPortfolio = platform.portfolio();

        if (fetchedPortfolio instanceof PortfolioResponse.Other other) {
            logger.info("portfolio fetch failed {}", other);
        }

        if (fetchedInstruments instanceof InstrumentsResponse.Other other) {
            logger.info("instruments fetch failed {}", other);
        }

        if (fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio && fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
            logPortfolio(portfolio, platform);
            sell(platform, portfolio, instruments);
            buy(platform, portfolio, instruments);
        }
    }

    private static void logPortfolio(PortfolioResponse.Portfolio portfolio, Platform platform) {
        for (final var element : portfolio.portfolio()) {
            final var instrument = element.instrument();
            final var orders = platform.orders(new OrdersRequest(instrument));
            logger.info("instrument {} has orders {}", instrument, orders);
        }
    }

    private static void buy(Platform platform, PortfolioResponse.Portfolio portfolio, InstrumentsResponse.Instruments instruments) {
        processInstruments(platform, instruments, ThreeDaysTrend::getOptionalBuyStats, e -> requestBuy(e, portfolio, platform));
    }
    private static void sell(Platform platform, PortfolioResponse.Portfolio portfolio, InstrumentsResponse.Instruments instruments) {
        processInstruments(platform, instruments, ThreeDaysTrend::getOptionalSellStats, e -> requestSell(e, portfolio, platform));
    }

    private static void processInstruments(Platform platform,
                                           InstrumentsResponse.Instruments instruments,
                                           Function<Map.Entry<String, HistoryResponse>, Optional<Stats>> historyResponseMapper,
                                           Consumer<Stats> statsProcessor) {
        instruments.available().stream()
                .collect(Collectors.toMap(instrument -> instrument.symbol(), instrument -> platform.history(new HistoryRequest(instrument))))
                .entrySet().stream()
                .filter(e -> e.getValue() instanceof HistoryResponse.History)
                .map(historyResponseMapper)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(Stats::delta).reversed())
                .limit(2) // buy/sell max N instruments
                .forEach(statsProcessor);
    }

    private static Optional<Stats> getOptionalBuyStats(final Map.Entry<String, HistoryResponse> elem) {
        return getOptionalStats(elem,
                averages -> averages.size() == 3 && (averages.get(0).average() > averages.get(1).average() && averages.get(1).average() > averages.get(2).average()) // process only if have three data points and trend was positive
        );
    }
    private static Optional<Stats> getOptionalSellStats(final Map.Entry<String, HistoryResponse> elem) {
        return getOptionalStats(elem,
                averages -> averages.size() == 3 && (averages.get(0).average() < averages.get(1).average() && averages.get(1).average() < averages.get(2).average()) // process only if have three data points and trend was negative
        );
    }
    private static Optional<Stats> getOptionalStats(Map.Entry<String, HistoryResponse> elem, Predicate<List<DayAverage>> isValidAverages) {
        // get stats from history.
        // stats: average price per day for three days
        // return only if found data for at least three days
        HistoryResponse.History correct = (HistoryResponse.History) elem.getValue();
        List<DayAverage> averages = correct.bought().stream()
                .collect(Collectors.groupingBy(e -> LocalDate.ofInstant(e.created(), ZoneId.systemDefault())))
                .entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream()
                                .mapToLong(b -> b.offer().price())
                                .average())
                )
                .entrySet().stream()
                .map(e -> new DayAverage(e.getKey(), e.getValue().orElse(0)))
                .sorted(Comparator.comparing(DayAverage::date).reversed())
                .limit(3)
                .collect(Collectors.toList());
        if (isValidAverages.test(averages)) {
            double delta = (averages.get(0).average() - averages.get(2).average()) / 2;
            long prediction = Math.round(averages.get(0).average() + delta);
            return Optional.of(new Stats(elem.getKey(), prediction, delta, averages));
        }
        return Optional.empty();
    }

    private static void requestBuy(Stats stats, PortfolioResponse.Portfolio portfolio, Platform platform) {
        logger.info(String.format("BUY %s: prediction=%s, delta=%s, A=%s", stats.instrumentSymbol, stats.prediction, stats.delta, stats.averages.toString()));
        final long bid = stats.averages.get(0).average.longValue();
        final var qty = portfolio.cash() / 10 / bid;
        final var buyRequest = new SubmitOrderRequest.Buy(stats.instrumentSymbol, UUID.randomUUID().toString(), qty, bid);
        final var orderResponse = platform.buy(buyRequest);
        logger.info("order {} placed with response {}", buyRequest, orderResponse);
    }

    private static void requestSell(Stats stats, PortfolioResponse.Portfolio portfolio, Platform platform) {
        logger.info(String.format("SELL %s: prediction=%s, delta=%s, A=%s", stats.instrumentSymbol, stats.prediction, stats.delta, stats.averages.toString()));
        final long qty = portfolio.portfolio().stream()
                .filter(element -> element.instrument().symbol().equals(stats.instrumentSymbol))
                .findAny()
                .map(PortfolioResponse.Portfolio.Element::qty)
                .orElse(minQty);
        final long ask = stats.prediction;
        final var sellRequest = new SubmitOrderRequest.Sell(stats.instrumentSymbol, UUID.randomUUID().toString(), qty, ask);
        final var orderResponse = platform.sell(sellRequest);
        logger.info("order {} placed with response {}", sellRequest, orderResponse);
    }
}
