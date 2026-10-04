package demo

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val clock: Clock,
    @param:Value("\${demo.limit}") private val limit: Int,
    private val prefix: String,
    private val orderServiceProvider: ObjectProvider<OrderService>,
    private val handlers: List<Handler>,
)
