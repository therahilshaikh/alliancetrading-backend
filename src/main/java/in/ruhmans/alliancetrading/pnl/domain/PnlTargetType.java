package in.ruhmans.alliancetrading.pnl.domain;

public enum PnlTargetType {
    /** One member (needs userId); their amount is split across their ACTIVE investments. */
    MEMBER,
    /** Every member with at least one ACTIVE investment. */
    ALL
}
