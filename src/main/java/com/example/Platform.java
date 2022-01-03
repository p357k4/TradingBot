package com.example;

import com.example.model.*;
import com.example.model.order.Instrument;
import com.example.model.order.ValidatedOrder;

public interface Platform {

    Portfolio portfolio();

    ValidatedOrder buy(SubmitOrder.Buy buyOrder);

    ValidatedOrder sell(SubmitOrder.Sell sellOrder);

    History history(Instrument instrument);

    Orders orders(Instrument instrument);

    Instruments instruments();

    Submitted submitted();

    Processed processed();
}
