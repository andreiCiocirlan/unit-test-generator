package fixtures;

public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository,
                        PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    @Override
    @Transactional
    public void cleanup() throws IOException {
        orderRepository.deleteAll();
    }

    public Receipt placeOrder(Order order) {

        if (order == null) {
            throw new IllegalArgumentException("order");
        }

        if (!paymentClient.isAvailable()) {
            throw new IllegalStateException("payment down");
        }

        try {
            Receipt receipt = paymentClient.charge(order.getTotal());
            orderRepository.save(order);
            return receipt;
        } catch (PaymentException e) {
            orderRepository.saveAsFailed(order);
            throw new OrderFailedException(order.getId(), e);
        }
    }
}