import pytest
from pydantic import ValidationError
from backend.app.models import HoldingOrderIntent


def test_holding_intent_is_long_only_cnc_lifecycle():
    buy = HoldingOrderIntent(
        idempotency_key="abcdef123456",
        strategy_id="s1",
        symbol="TIMEX",
        action="BUY_HOLDING",
        quantity=10,
        expected_ltp=700,
    )
    assert buy.action == "BUY_HOLDING"

    sell = HoldingOrderIntent(
        idempotency_key="abcdef123457",
        strategy_id="s1",
        symbol="TIMEX",
        action="SELL_OWNED_HOLDING",
        quantity=10,
        expected_ltp=710,
    )
    assert sell.action == "SELL_OWNED_HOLDING"

    with pytest.raises(ValidationError):
        HoldingOrderIntent(
            idempotency_key="abcdef123458",
            strategy_id="s1",
            symbol="TIMEX",
            action="SHORT",
            quantity=10,
        )

    with pytest.raises(ValidationError):
        HoldingOrderIntent(
            idempotency_key="abcdef123459",
            strategy_id="s1",
            symbol="bad symbol",
            action="BUY_HOLDING",
            quantity=0,
        )
