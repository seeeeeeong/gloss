package demo

@RestController
class OrderController {
    @GetMapping("/orders")
    fun orders(): ResponseEntity<Any> = ResponseEntity.ok("orders")
}
