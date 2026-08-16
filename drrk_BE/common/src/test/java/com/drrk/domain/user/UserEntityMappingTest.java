package com.drrk.domain.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UserEntityMappingTest {

	private static final String USER_CLASS_NAME = "com.drrk.domain.user.User";
	private static final String LOGIN_TYPE_CLASS_NAME = "com.drrk.domain.user.LoginType";
	private static final String USER_STATUS_CLASS_NAME = "com.drrk.domain.user.UserStatus";

	@Test
	void mapsUserEntityToUsersTable() throws ReflectiveOperationException {
		Class<?> userClass = userClass();

		assertTrue(userClass.isAnnotationPresent(Entity.class));
		Table table = userClass.getAnnotation(Table.class);
		assertNotNull(table);
		assertEquals("users", table.name());
	}

	@Test
	void mapsIdAsIdentityPrimaryKey() throws ReflectiveOperationException {
		Field id = field("id");

		assertEquals(Long.class, id.getType());
		assertTrue(id.isAnnotationPresent(Id.class));
		GeneratedValue generatedValue = id.getAnnotation(GeneratedValue.class);
		assertNotNull(generatedValue);
		assertEquals(GenerationType.IDENTITY, generatedValue.strategy());
		assertColumn(id, "id", false);
	}

	@Test
	void mapsStringAndEnumColumns() throws ReflectiveOperationException {
		assertSizedColumn("email", String.class, "email", false, 320);
		assertSizedColumn("password", String.class, "password", true, 255);
		assertSizedColumn("loginType", classFor(LOGIN_TYPE_CLASS_NAME), "login_type", false, 20);
		assertSizedColumn("providerUserId", String.class, "provider_user_id", true, 255);
		assertSizedColumn("nickname", String.class, "nickname", false, 100);
		assertSizedColumn("status", classFor(USER_STATUS_CLASS_NAME), "status", false, 20);

		assertStringEnum("loginType");
		assertStringEnum("status");
	}

	@Test
	void mapsTimestampColumns() throws ReflectiveOperationException {
		assertTypedColumn("emailVerifiedAt", LocalDateTime.class, "email_verified_at", true);
		assertTypedColumn("createdAt", LocalDateTime.class, "created_at", false);
		assertTypedColumn("deletedAt", LocalDateTime.class, "deleted_at", true);

		assertFalse(field("createdAt").getAnnotation(Column.class).updatable());
	}

	@Test
	void definesOnlySupportedLoginTypes() throws ReflectiveOperationException {
		assertArrayEquals(
				new String[] {"EMAIL", "GOOGLE"},
				enumNames(LOGIN_TYPE_CLASS_NAME)
		);
	}

	@Test
	void definesOnlySupportedUserStatuses() throws ReflectiveOperationException {
		assertArrayEquals(
				new String[] {"ACTIVE", "SUSPENDED", "WITHDRAWN"},
				enumNames(USER_STATUS_CLASS_NAME)
		);
	}

	private static Class<?> userClass() throws ClassNotFoundException {
		return classFor(USER_CLASS_NAME);
	}

	private static Class<?> classFor(String className) throws ClassNotFoundException {
		return Class.forName(className);
	}

	private static Field field(String fieldName) throws ReflectiveOperationException {
		return userClass().getDeclaredField(fieldName);
	}

	private static void assertSizedColumn(
			String fieldName,
			Class<?> expectedType,
			String columnName,
			boolean nullable,
			int length
	) throws ReflectiveOperationException {
		Field field = field(fieldName);
		assertEquals(expectedType, field.getType());
		Column column = assertColumn(field, columnName, nullable);
		assertEquals(length, column.length());
	}

	private static void assertTypedColumn(
			String fieldName,
			Class<?> expectedType,
			String columnName,
			boolean nullable
	) throws ReflectiveOperationException {
		Field field = field(fieldName);
		assertEquals(expectedType, field.getType());
		assertColumn(field, columnName, nullable);
	}

	private static Column assertColumn(Field field, String columnName, boolean nullable) {
		Column column = field.getAnnotation(Column.class);
		assertNotNull(column);
		assertEquals(columnName, column.name());
		assertEquals(nullable, column.nullable());
		return column;
	}

	private static void assertStringEnum(String fieldName) throws ReflectiveOperationException {
		Enumerated enumerated = field(fieldName).getAnnotation(Enumerated.class);
		assertNotNull(enumerated);
		assertEquals(EnumType.STRING, enumerated.value());
	}

	private static String[] enumNames(String className) throws ClassNotFoundException {
		Class<?> enumClass = classFor(className);
		assertTrue(enumClass.isEnum());
		return Arrays.stream(enumClass.getEnumConstants())
				.map(value -> ((Enum<?>) value).name())
				.toArray(String[]::new);
	}
}
