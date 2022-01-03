package com.example;

import com.example.model.History;
import com.example.model.Instruments;
import com.example.model.Portfolio;
import com.example.model.SubmitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrdersController implements Runnable {

    private static final long minQty = 10;
    private static final long minAsk = 13;
    private static final long minBid = 79;

    private static final Logger logger = LoggerFactory.getLogger(OrdersController.class);

    private final Platform marketPlugin;

    private final Random rg = new Random();

    public OrdersController(Platform marketPlugin) {
        this.marketPlugin = marketPlugin;
    }

    @Override
    public void run() {
        final var fetchedInstruments = marketPlugin.instruments();
        final var fetchedPortfolio = marketPlugin.portfolio();

        if (fetchedPortfolio instanceof Portfolio.Failed failed) {
            logger.error("portfolio call returned an error {}", failed);
        }

        if (fetchedPortfolio instanceof Portfolio.Current portfolio && fetchedInstruments instanceof Instruments.Correct instruments) {
            portfolio
                    .portfolio()
                    .stream()
                    .map(Portfolio.PortfolioElement::instrument)
                    .forEach( instrument -> {
                        final var orders = marketPlugin.orders(instrument);
                        logger.info("instrument {} has orders {}", instrument, orders);
                    });

            final var selectedForBuy = instruments
                    .available()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.10)
                    .toList();

            selectedForBuy
                    .stream()
                    .map(pe -> {
                        final var history = marketPlugin.history(pe);

                        long bid;
                        if (history instanceof History.Correct correct) {
                            bid = (long) (
                                    1.1 * correct
                                            .bought()
                                            .stream()
                                            .mapToLong(b -> b.offer().price())
                                            .average()
                                            .orElse(minBid)
                            );
                        } else {
                            bid = minBid;
                        }

                        final var qty = rg.nextInt((int) (portfolio.cash() / 4 / bid));
                        final var buy = new SubmitOrder.Buy(pe.symbol(), UUID.randomUUID().toString(), qty, bid);

                        logger.info("order to submit {}", buy);
                        return buy;
                    })
                    .map(marketPlugin::buy)
                    .forEach(vo -> logger.info("order placed with response {}", vo));

            final var selectedForSell = portfolio
                    .portfolio()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.20)
                    .toList();

            selectedForSell
                    .stream()
                    .map(pe -> {
                        logger.info("portfolio element {}", pe);
                        final var history = marketPlugin.history(pe.instrument());

                        long ask;
                        if (history instanceof History.Correct correct) {
                            ask = (long) (
                                    0.9 * correct
                                            .bought()
                                            .stream()
                                            .mapToLong(b -> b.offer().price())
                                            .average()
                                            .orElse(minAsk)
                            );
                        } else {
                            ask = minAsk;
                        }

                        final long qty = Math.min(pe.qty(), minQty);
                        final var sell = new SubmitOrder.Sell(pe.instrument().symbol(), UUID.randomUUID().toString(), qty, ask);

                        logger.info("order to submit {}", sell);
                        return sell;
                    })
                    .map(marketPlugin::sell)
                    .forEach(vo -> logger.info("order placed with response {}", vo));
        }
    }
}
