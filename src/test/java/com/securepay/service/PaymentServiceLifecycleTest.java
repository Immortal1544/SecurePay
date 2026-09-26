package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.razorpay.RazorpayClient;
import com.securepay.dto.payment.VerifyPaymentRequest;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Payment;
import com.securepay.entity.PaymentStatus;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.exception.InvalidPaymentException;
import com.securepay.exception.InvalidPaymentStateException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.PaymentRepository;
import com.securepay.repository.UserRepository;

class PaymentServiceLifecycleTest {

	private static final Long USER_ID = 10L;
	private static final Long ORDER_ID = 20L;
	private static final String EMAIL = "customer@example.com";
	private static final String KEY_SECRET = "test-key-secret";
	private static final String RAZORPAY_ORDER_ID = "order_test_123";
	private static final String RAZORPAY_PAYMENT_ID = "pay_test_123";

	private UserRepository userRepository;
	private OrderRepository orderRepository;
	private PaymentRepository paymentRepository;
	private InventoryReservationService inventoryReservationService;
	private PaymentService paymentService;
	private User user;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		orderRepository = mock(OrderRepository.class);
		paymentRepository = mock(PaymentRepository.class);
		inventoryReservationService = mock(InventoryReservationService.class);
		paymentService = new PaymentService(
				userRepository,
				orderRepository,
				paymentRepository,
				inventoryReservationService,
				(RazorpayClient) null,
				"test-key-id",
				KEY_SECRET,
				"test-webhook-secret");
		user = new User("Customer", EMAIL, "password", Role.USER);
		user.setId(USER_ID);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(EMAIL, "", List.of()));
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void paymentCreationIsAllowedForCreatedOrder() {
		Order order = order(OrderStatus.CREATED);
		when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var response = paymentService.createPayment(ORDER_ID);

		assertEquals(PaymentStatus.CREATED, response.getStatus());
		verify(paymentRepository).save(any(Payment.class));
	}

	@Test
	void existingPaymentCanBeReusedForPaymentPendingOrder() {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

		var response = paymentService.createPayment(ORDER_ID);

		assertEquals(PaymentStatus.PENDING, response.getStatus());
		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	void razorpayOrderCanBeReusedForCreatedAndPaymentPendingOrders() {
		for (OrderStatus status : List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)) {
			Order order = order(status);
			Payment payment = payment(order, PaymentStatus.PENDING);
			when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
			when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

			var response = paymentService.createRazorpayOrder(ORDER_ID);

			assertEquals(RAZORPAY_ORDER_ID, response.getRazorpayOrderId(), status.name());
		}
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void paymentAndRazorpayOrderCreationRejectIneligibleOrderStates() {
		for (OrderStatus status : List.of(
				OrderStatus.PAID,
				OrderStatus.PROCESSING,
				OrderStatus.SHIPPED,
				OrderStatus.DELIVERED,
				OrderStatus.CANCELLED)) {
			SecurityContextHolder.clearContext();
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(EMAIL, "", List.of()));
			Order order = order(status);
			when(orderRepository.findByIdAndUserIdForUpdate(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

			assertThrows(InvalidPaymentStateException.class,
					() -> paymentService.createPayment(ORDER_ID), status.name());
			assertThrows(InvalidPaymentStateException.class,
					() -> paymentService.createRazorpayOrder(ORDER_ID), status.name());
		}

		verifyNoInteractions(paymentRepository);
	}

	@Test
	void validVerificationMarksPendingPaymentAndOrderSuccessful() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
		VerifyPaymentRequest request = request(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID,
				signature(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID));

		var response = paymentService.verifyPayment(ORDER_ID, request);

		assertEquals(PaymentStatus.SUCCESS, response.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository).save(payment);
		verify(orderRepository).save(order);
	}

	@Test
	void repeatedValidVerificationAfterSuccessDoesNotWriteOrChangeSuccessfulState() throws Exception {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		payment.setRazorpayPaymentId(RAZORPAY_PAYMENT_ID);
		LocalDateTime previousUpdatedAt = LocalDateTime.of(2024, 1, 2, 3, 4);
		payment.setUpdatedAt(previousUpdatedAt);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
		String repeatedPaymentId = "pay_other_valid";

		var response = paymentService.verifyPayment(ORDER_ID, request(
				RAZORPAY_ORDER_ID,
				repeatedPaymentId,
				signature(RAZORPAY_ORDER_ID, repeatedPaymentId)));

		assertEquals(PaymentStatus.SUCCESS, response.getStatus());
		assertEquals(RAZORPAY_PAYMENT_ID, payment.getRazorpayPaymentId());
		assertEquals(previousUpdatedAt, payment.getUpdatedAt());
		assertEquals(OrderStatus.PAID, order.getStatus());
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void mismatchedRazorpayOrderIdIsRejected() {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

		assertThrows(InvalidPaymentException.class,
				() -> paymentService.verifyPayment(ORDER_ID,
						request("order_mismatch", RAZORPAY_PAYMENT_ID, "signature")));
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void invalidSignatureIsRejected() throws Exception {
		Order order = order(OrderStatus.PAYMENT_PENDING);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

		assertThrows(InvalidPaymentException.class,
				() -> paymentService.verifyPayment(ORDER_ID,
						request(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID, "invalid-signature")));
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void anotherCustomersOrderCannotBeVerified() {
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> paymentService.verifyPayment(ORDER_ID,
						request(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID, "signature")));
		verifyNoInteractions(paymentRepository);
	}

	@Test
	void successfulPaymentStillRequiresValidSignature() {
		Order order = order(OrderStatus.PAID);
		Payment payment = payment(order, PaymentStatus.SUCCESS);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

		assertThrows(InvalidPaymentException.class,
				() -> paymentService.verifyPayment(ORDER_ID,
						request(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID, "invalid-signature")));
		verify(paymentRepository, never()).save(any(Payment.class));
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void validLateVerificationDoesNotResurrectCancelledOrderOrItsReleasedReservation() throws Exception {
		Order order = order(OrderStatus.CANCELLED);
		order.setInventoryReserved(false);
		Payment payment = payment(order, PaymentStatus.PENDING);
		when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
		when(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

		var response = paymentService.verifyPayment(ORDER_ID, request(
				RAZORPAY_ORDER_ID,
				RAZORPAY_PAYMENT_ID,
				signature(RAZORPAY_ORDER_ID, RAZORPAY_PAYMENT_ID)));

		assertEquals(PaymentStatus.SUCCESS, response.getStatus());
		assertEquals(OrderStatus.CANCELLED, order.getStatus());
		assertEquals(false, order.getInventoryReserved());
		verify(paymentRepository).save(payment);
		verify(orderRepository, never()).save(order);
		verifyNoInteractions(inventoryReservationService);
	}

	private Order order(OrderStatus status) {
		Order order = new Order(user, BigDecimal.TEN, status);
		order.setId(ORDER_ID);
		return order;
	}

	private Payment payment(Order order, PaymentStatus status) {
		Payment payment = new Payment(order, BigDecimal.TEN, status);
		payment.setId(30L);
		payment.setRazorpayOrderId(RAZORPAY_ORDER_ID);
		return payment;
	}

	private VerifyPaymentRequest request(String orderId, String paymentId, String signature) {
		VerifyPaymentRequest request = new VerifyPaymentRequest();
		request.setRazorpayOrderId(orderId);
		request.setRazorpayPaymentId(paymentId);
		request.setRazorpaySignature(signature);
		return request;
	}

	private String signature(String orderId, String paymentId) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return java.util.HexFormat.of().formatHex(
				mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
	}
}
