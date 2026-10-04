package demo

@RestController
class OrderController(
    private val orderService: OrderService,
) {
    @GetMapping("/orders")
    fun orders(): OrderResponse = orderService.orders().toResponse()

    @GetMapping("/orders/all")
    fun all(): List<OrderResponse> = orderService.all().map { it.toResponse() }

    @PutMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update() {
        orderService.update()
    }
}
