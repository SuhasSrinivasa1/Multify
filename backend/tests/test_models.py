import pytest
from pydantic import ValidationError
from backend.app.models import OrderIntent


def test_order_intent_requires_valid_symbol_and_qty():
    x = OrderIntent(idempotency_key="abcdef123456", strategy_id="s1", symbol="TIMEX", side="BUY", quantity=10, expected_ltp=700)
    assert x.wave == 1
    with pytest.raises(ValidationError):
        OrderIntent(idempotency_key="abcdef123456", strategy_id="s1", symbol="bad symbol", side="BUY", quantity=0)
