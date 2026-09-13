package clouddev1stack.manager

sealed class AuctionResult {
    data class Success(val message: String) : AuctionResult()
    data class Failure(val reason: String) : AuctionResult()
}
