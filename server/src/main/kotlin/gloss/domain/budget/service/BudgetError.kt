package gloss.domain.budget.service

import gloss.common.error.ErrorCode
import gloss.domain.budget.domain.SpendDecision
import org.springframework.http.HttpStatus

enum class BudgetError(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    LIMIT_NOT_SET(HttpStatus.CONFLICT, "budget.limit_not_set", "월 사용 한도를 먼저 정해 주세요."),
    LIMIT_REACHED(
        HttpStatus.CONFLICT,
        "budget.limit_reached",
        "이번 달 사용 한도에 닿았어요. 한도를 올리거나 다음 달을 기다려 주세요.",
    ),
    TOO_MANY_IN_FLIGHT(
        HttpStatus.TOO_MANY_REQUESTS,
        "budget.too_many_in_flight",
        "진행 중인 요청이 많아요. 하나가 끝난 뒤 다시 시도해 주세요.",
    ),
    TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "budget.too_frequent", "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요."),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST, "budget.invalid_limit", "0에서 1,000 사이 금액으로 정해 주세요."),
    ;

    companion object {
        fun of(decision: SpendDecision): BudgetError? =
            when (decision) {
                SpendDecision.Allowed -> null
                SpendDecision.LimitNotSet -> LIMIT_NOT_SET
                SpendDecision.LimitReached -> LIMIT_REACHED
                SpendDecision.TooManyInFlight -> TOO_MANY_IN_FLIGHT
                SpendDecision.TooFrequent -> TOO_FREQUENT
            }
    }
}
