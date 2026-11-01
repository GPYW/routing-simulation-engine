package com.uom.engine.numerical;

public record MarketConditions(double fuelPricePerLitre, double cargoValue, int intervals) {
    public MarketConditions {
        if (!(fuelPricePerLitre > 0)) throw new IllegalArgumentException("fuel price must be > 0");
        if (!(cargoValue >= 0))       throw new IllegalArgumentException("cargo value must be >= 0");
        if (intervals < 2)            throw new IllegalArgumentException("need at least 2 intervals");
        if (intervals % 2 != 0) intervals++;   // Simpson needs an even count
    }

    public static MarketConditions defaults() {
        return new MarketConditions(350.0, 2_000_000.0, 32);
    }

    public MarketConditions withFuelPrice(double p)  { return new MarketConditions(p, cargoValue, intervals); }
    public MarketConditions withCargoValue(double v) { return new MarketConditions(fuelPricePerLitre, v, intervals); }
}