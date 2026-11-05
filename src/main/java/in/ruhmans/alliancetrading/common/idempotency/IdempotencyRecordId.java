package in.ruhmans.alliancetrading.common.idempotency;

import java.io.Serializable;
import java.util.Objects;

public class IdempotencyRecordId implements Serializable {

    private String scope;
    private String key;

    public IdempotencyRecordId() {}

    public IdempotencyRecordId(String scope, String key) {
        this.scope = scope;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyRecordId that)) return false;
        return Objects.equals(scope, that.scope) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, key);
    }
}
