package demo

@Service
class OrderService(
    private val orders: OrderRepository,
    private val clock: Clock,
    @param:Qualifier("primary") private val payments: PaymentGateway,
    private val lazyOrders: ObjectProvider<OrderService>,
)
