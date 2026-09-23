package com.securepay.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.securepay.dto.payment.CreatePaymentResponse;
import com.securepay.dto.payment.PaymentResponse;
import com.securepay.dto.payment.RazorpayOrderResponse;
import com.securepay.dto.payment.VerifyPaymentRequest;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Payment;
import com.securepay.entity.PaymentStatus;
import com.securepay.entity.User;
import com.securepay.exception.InvalidPaymentException;
import com.securepay.exception.InvalidWebhookException;
import com.securepay.exception.RazorpayPaymentException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.PaymentRepository;
import com.securepay.repository.UserRepository;

@Service
public class PaymentService {

	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final RazorpayClient razorpayClient;
	private final String razorpayKeyId;
	private final String razorpayKeySecret;
	private final String razorpayWebhookSecret;

	public PaymentService(
			UserRepository userRepository,
			OrderRepository orderRepository,
			PaymentRepository paymentRepository,
			RazorpayClient razorpayClient,
			@Value("${razorpay.key.id}") String razorpayKeyId,
			@Value("${razorpay.key.secret}") String razorpayKeySecret,
			@Value("${razorpay.webhook.secret}") String razorpayWebhookSecret) {
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.paymentRepository = paymentRepository;
		this.razorpayClient = razorpayClient;
		this.razorpayKeyId = razorpayKeyId;
		this.razorpayKeySecret = razorpayKeySecret;
		this.razorpayWebhookSecret = razorpayWebhookSecret;
	}

	@Transactional
	public void processRazorpayWebhook(String rawPayload, String signature) {
		verifyWebhookSignature(rawPayload, signature);

		JSONObject webhook;
		try {
			webhook = new JSONObject(rawPayload);
		} catch (JSONException exception) {
			throw new InvalidWebhookException("Malformed webhook payload", exception);
		}

		String event = textAt(webhook, "event");
		switch (event) {
		case "payment.captured" -> processCapturedPayment(webhook);
		case "payment.failed" -> processFailedPayment(webhook);
		case "order.paid" -> processPaidOrder(webhook);
		default -> {
			// Ignore webhook events that are not part of the payment state contract.
		}
		}
	}

	@Transactional
	public CreatePaymentResponse createPayment(Long orderId) {
		User user = getCurrentUser();
		Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Order not found with id: " + orderId));

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseGet(() -> paymentRepository.save(
						new Payment(order, order.getTotalAmount(), PaymentStatus.CREATED)));

		return toCreatePaymentResponse(payment);
	}

	@Transactional
	public PaymentResponse getPaymentForOrder(Long orderId) {
		User user = getCurrentUser();
		Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Order not found with id: " + orderId));

		Payment payment = paymentRepository.findByOrderId(order.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Payment not found for order with id: " + orderId));

		return toPaymentResponse(payment);
	}

	@Transactional
	public List<PaymentResponse> getCurrentUserPayments() {
		User user = getCurrentUser();
		return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
				.stream()
				.map(order -> paymentRepository.findByOrderId(order.getId()))
				.flatMap(java.util.Optional::stream)
				.map(this::toPaymentResponse)
				.toList();
	}

	@Transactional
	public PaymentResponse verifyPayment(Long orderId, VerifyPaymentRequest request) {
		User user = getCurrentUser();
		Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Order not found with id: " + orderId));

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Payment not found for order with id: " + orderId));

		if (!java.util.Objects.equals(request.getRazorpayOrderId(), payment.getRazorpayOrderId())) {
			throw new InvalidPaymentException("Payment verification failed");
		}

		String message = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
		String expectedSignature;
		try {
			expectedSignature = generateSignature(message, razorpayKeySecret);
		} catch (GeneralSecurityException exception) {
			throw new InvalidPaymentException("Unable to verify payment signature", exception);
		}

		boolean signatureMatches = MessageDigest.isEqual(
				expectedSignature.getBytes(StandardCharsets.UTF_8),
				request.getRazorpaySignature().getBytes(StandardCharsets.UTF_8));
		if (!signatureMatches) {
			throw new InvalidPaymentException("Payment verification failed");
		}

		payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
		payment.setStatus(PaymentStatus.SUCCESS);
		paymentRepository.save(payment);

		order.setStatus(OrderStatus.PAID);
		orderRepository.save(order);

		return toPaymentResponse(payment);
	}

	@Transactional
	public RazorpayOrderResponse createRazorpayOrder(Long orderId) {
		User user = getCurrentUser();
		Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Order not found with id: " + orderId));

		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Payment not found for order with id: " + orderId));

		if (payment.getRazorpayOrderId() != null && !payment.getRazorpayOrderId().isBlank()) {
			return toRazorpayOrderResponse(payment, payment.getRazorpayOrderId());
		}

		int amountInPaise = payment.getAmount()
				.multiply(BigDecimal.valueOf(100))
				.toBigIntegerExact()
				.intValueExact();
		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", amountInPaise);
		orderRequest.put("currency", "INR");
		orderRequest.put("receipt", "securepay-order-" + order.getId() + "-payment-" + payment.getId());

		try {
			com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
			String razorpayOrderId = razorpayOrder.get("id");
			payment.setRazorpayOrderId(razorpayOrderId);
			payment.setStatus(PaymentStatus.PENDING);
			Payment savedPayment = paymentRepository.save(payment);
			order.setStatus(OrderStatus.PAYMENT_PENDING);
			orderRepository.save(order);
			return toRazorpayOrderResponse(savedPayment, razorpayOrderId);
		} catch (RazorpayException exception) {
			throw new RazorpayPaymentException("Unable to communicate with Razorpay", exception);
		}
	}

	private void processCapturedPayment(JSONObject webhook) {
		String razorpayOrderId = textAt(webhook, "payload", "payment", "entity", "order_id");
		String razorpayPaymentId = textAt(webhook, "payload", "payment", "entity", "id");
		Payment payment = findPayment(razorpayOrderId);

		if (payment == null || payment.getStatus() == PaymentStatus.SUCCESS) {
			return;
		}

		if (razorpayPaymentId != null) {
			payment.setRazorpayPaymentId(razorpayPaymentId);
		}
		payment.setStatus(PaymentStatus.SUCCESS);
		payment.getOrder().setStatus(OrderStatus.PAID);
		paymentRepository.save(payment);
		orderRepository.save(payment.getOrder());
	}

	private void processFailedPayment(JSONObject webhook) {
		String razorpayOrderId = textAt(webhook, "payload", "payment", "entity", "order_id");
		Payment payment = findPayment(razorpayOrderId);

		if (payment == null || payment.getStatus() == PaymentStatus.SUCCESS) {
			return;
		}

		payment.setStatus(PaymentStatus.FAILED);
		paymentRepository.save(payment);
	}

	private void processPaidOrder(JSONObject webhook) {
		String razorpayOrderId = textAt(webhook, "payload", "order", "entity", "id");
		Payment payment = findPayment(razorpayOrderId);

		if (payment == null) {
			return;
		}

		payment.setStatus(PaymentStatus.SUCCESS);
		payment.getOrder().setStatus(OrderStatus.PAID);
		paymentRepository.save(payment);
		orderRepository.save(payment.getOrder());
	}

	private Payment findPayment(String razorpayOrderId) {
		if (razorpayOrderId == null) {
			return null;
		}

		return paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
	}

	private String textAt(JSONObject root, String... path) {
		JSONObject current = root;
		for (int index = 0; index < path.length - 1; index++) {
			current = current.optJSONObject(path[index]);
			if (current == null) {
				return null;
			}
		}

		String value = current.optString(path[path.length - 1], null);
		return value == null || value.isBlank() ? null : value;
	}

	private void verifyWebhookSignature(String rawPayload, String signature) {
		if (rawPayload == null || signature == null || razorpayWebhookSecret == null
				|| razorpayWebhookSecret.isBlank()) {
			throw new InvalidWebhookException("Invalid webhook signature");
		}

		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
					razorpayWebhookSecret.getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"));
			String calculatedSignature = java.util.HexFormat.of()
					.formatHex(mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)));
			boolean valid = MessageDigest.isEqual(
					calculatedSignature.getBytes(StandardCharsets.UTF_8),
					signature.getBytes(StandardCharsets.UTF_8));
			if (!valid) {
				throw new InvalidWebhookException("Invalid webhook signature");
			}
		} catch (GeneralSecurityException exception) {
			throw new InvalidWebhookException("Unable to verify webhook signature", exception);
		}
	}

	private User getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			throw new IllegalStateException("No authenticated user found");
		}

		return userRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Authenticated user not found with email: " + authentication.getName()));
	}

	private CreatePaymentResponse toCreatePaymentResponse(Payment payment) {
		CreatePaymentResponse response = new CreatePaymentResponse();
		response.setPaymentId(payment.getId());
		response.setOrderId(payment.getOrder().getId());
		response.setAmount(payment.getAmount());
		response.setStatus(payment.getStatus());
		response.setRazorpayOrderId(payment.getRazorpayOrderId());
		return response;
	}

	private PaymentResponse toPaymentResponse(Payment payment) {
		PaymentResponse response = new PaymentResponse();
		response.setPaymentId(payment.getId());
		response.setOrderId(payment.getOrder().getId());
		response.setAmount(payment.getAmount());
		response.setStatus(payment.getStatus());
		response.setRazorpayOrderId(payment.getRazorpayOrderId());
		response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
		response.setCreatedAt(payment.getCreatedAt());
		response.setUpdatedAt(payment.getUpdatedAt());
		return response;
	}

	private RazorpayOrderResponse toRazorpayOrderResponse(Payment payment, String razorpayOrderId) {
		RazorpayOrderResponse response = new RazorpayOrderResponse();
		response.setPaymentId(payment.getId());
		response.setOrderId(payment.getOrder().getId());
		response.setAmount(payment.getAmount());
		response.setCurrency("INR");
		response.setRazorpayOrderId(razorpayOrderId);
		response.setRazorpayKeyId(razorpayKeyId);
		return response;
	}

	private String generateSignature(String message, String secret) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return java.util.HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
	}
}