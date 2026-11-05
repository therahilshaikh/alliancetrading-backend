package in.ruhmans.alliancetrading.pnl.domain;

/** How a batch's lines express P&L. Both apply to every target investment individually. */
public enum PnlValueType {
    /** Every target investment receives the same rupee amount. */
    FIXED,
    /** Every target investment receives a percent of its own principal. */
    PERCENTAGE
}
