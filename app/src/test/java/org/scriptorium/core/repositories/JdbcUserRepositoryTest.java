package org.scriptorium.core.repositories;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scriptorium.core.domain.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.io.File;

import org.scriptorium.core.exceptions.DuplicateEmailException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JdbcUserRepositoryTest {

    private static final String TEST_DB_URL = "jdbc:sqlite:test.db"; // File-based database for testing
    private JdbcUserRepository userRepository;

    @BeforeEach
    void setUp() throws SQLException {
        // Ensure the database file is deleted before each test
        new File("test.db").delete();

        try (Connection conn = DriverManager.getConnection(TEST_DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users;");
            String sql = "CREATE TABLE users (" +
                         " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         " firstName TEXT NOT NULL," +
                         " lastName TEXT NOT NULL," +
                         " email TEXT NOT NULL UNIQUE," +
                         " passwordHash TEXT NOT NULL," +
                         " street TEXT," +
                         " postalCode TEXT," +
                         " city TEXT," +
                         " country TEXT" +
                         ");";
            stmt.execute(sql);
        }
        userRepository = new JdbcUserRepository(TEST_DB_URL);
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Clean up after each test by deleting the database file
        new File("test.db").delete();
    }

    @Test
    void testSaveAndFindById() {
        User user = new User("Jane", "Doe", "jane.doe@example.com", "password123");
        user.setStreet("Main St");
        user.setPostalCode("12345");
        user.setCity("Anytown");
        user.setCountry("USA");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("Jane", savedUser.getFirstName());
        assertEquals("Doe", savedUser.getLastName());
        assertEquals("jane.doe@example.com", savedUser.getEmail());
        assertEquals("password123", savedUser.getPasswordHash()); // Check password hash

        Optional<User> foundUser = userRepository.findById(savedUser.getId());

        assertTrue(foundUser.isPresent());
        assertEquals(savedUser.getId(), foundUser.get().getId());
        assertEquals(savedUser.getEmail(), foundUser.get().getEmail());
        assertEquals(savedUser.getPasswordHash(), foundUser.get().getPasswordHash()); // Check password hash
    }

    @Test
    void testUpdateUser() {
        User user = new User("John", "Doe", "john.doe@example.com", "initialPass");
        User savedUser = userRepository.save(user);

        savedUser.setFirstName("Jonathan");
        savedUser.setEmail("jonathan.doe@example.com");
        savedUser.setPasswordHash("newPass"); // Update password hash

        User updatedUser = userRepository.save(savedUser);

        assertEquals("Jonathan", updatedUser.getFirstName());
        assertEquals("jonathan.doe@example.com", updatedUser.getEmail());
        assertEquals("newPass", updatedUser.getPasswordHash());

        Optional<User> foundUser = userRepository.findById(updatedUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals("Jonathan", foundUser.get().getFirstName());
        assertEquals("jonathan.doe@example.com", foundUser.get().getEmail());
        assertEquals("newPass", foundUser.get().getPasswordHash());
    }

    @Test
    void testUpdateUserPartial() {
        User user = new User("Original", "Name", "original@example.com", "originalPass");
        user.setStreet("Old Street");
        user.setPostalCode("11111");
        user.setCity("Old City");
        user.setCountry("Old Country");
        User savedUser = userRepository.save(user);

        savedUser.setFirstName("Updated");
        savedUser.setEmail("updated@example.com");
        // Other fields remain unchanged, including passwordHash

        User updatedUser = userRepository.save(savedUser);

        assertEquals("Updated", updatedUser.getFirstName());
        assertEquals("Name", updatedUser.getLastName()); // Should be unchanged
        assertEquals("updated@example.com", updatedUser.getEmail());
        assertEquals("originalPass", updatedUser.getPasswordHash()); // Should be unchanged
        assertEquals("Old Street", updatedUser.getStreet()); // Should be unchanged
        assertEquals("11111", updatedUser.getPostalCode()); // Should be unchanged
        assertEquals("Old City", updatedUser.getCity()); // Should be unchanged
        assertEquals("Old Country", updatedUser.getCountry()); // Should be unchanged

        Optional<User> foundUser = userRepository.findById(updatedUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals("Updated", foundUser.get().getFirstName());
        assertEquals("Name", foundUser.get().getLastName());
        assertEquals("updated@example.com", foundUser.get().getEmail());
        assertEquals("originalPass", foundUser.get().getPasswordHash());
        assertEquals("Old Street", foundUser.get().getStreet());
        assertEquals("11111", foundUser.get().getPostalCode());
        assertEquals("Old City", foundUser.get().getCity());
        assertEquals("Old Country", foundUser.get().getCountry());
    }

    @Test
    void testFindAll() {
        User user1 = new User("Alice", "Smith", "alice@example.com", "pass1");
        User user2 = new User("Bob", "Johnson", "bob@example.com", "pass2");

        userRepository.save(user1);
        userRepository.save(user2);

        List<User> users = userRepository.findAll();
        assertEquals(2, users.size());
        assertTrue(users.stream().anyMatch(u -> u.getEmail().equals("alice@example.com")));
        assertTrue(users.stream().anyMatch(u -> u.getEmail().equals("bob@example.com")));
    }

    @Test
    void testDeleteById() {
        User user = new User("Charlie", "Brown", "charlie@example.com", "pass");
        User savedUser = userRepository.save(user);

        userRepository.deleteById(savedUser.getId());

        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertFalse(foundUser.isPresent());
    }

    @Test
    void testFindByEmail() {
        User user = new User("Email", "Finder", "find.me@example.com", "secret");
        userRepository.save(user);

        Optional<User> foundUser = userRepository.findByEmail("find.me@example.com");
        assertTrue(foundUser.isPresent());
        assertEquals("find.me@example.com", foundUser.get().getEmail());
        assertEquals("secret", foundUser.get().getPasswordHash());

        Optional<User> notFoundUser = userRepository.findByEmail("not.found@example.com");
        assertFalse(notFoundUser.isPresent());
    }

    @Test
    void testSaveDuplicateEmailThrowsException() {
        User user1 = new User("Test", "User", "duplicate@example.com", "pass1");
        userRepository.save(user1);

        User user2 = new User("Another", "User", "duplicate@example.com", "pass2");

        assertThrows(DuplicateEmailException.class, () -> {
            userRepository.save(user2);
        });
    }

    @Test
    void testUpdateToDuplicateEmailThrowsException() {
        User user1 = new User("Test", "User", "email1@example.com", "pass1");
        userRepository.save(user1);

        User user2 = new User("Another", "User", "email2@example.com", "pass2");
        User savedUser2 = userRepository.save(user2);

        savedUser2.setEmail("email1@example.com"); // Try to update to an existing email

        assertThrows(DuplicateEmailException.class, () -> {
            userRepository.save(savedUser2);
        });
    
        User user = new User("Full", "Name", "full.name@example.com", "fullPass");
        user.setStreet("123 Test St");
        user.setPostalCode("98765");
        user.setCity("Testville");
        user.setCountry("Testland");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("Full", savedUser.getFirstName());
        assertEquals("Name", savedUser.getLastName());
        assertEquals("full.name@example.com", savedUser.getEmail());
        assertEquals("fullPass", savedUser.getPasswordHash());
        assertEquals("123 Test St", savedUser.getStreet());
        assertEquals("98765", savedUser.getPostalCode());
        assertEquals("Testville", savedUser.getCity());
        assertEquals("Testland", savedUser.getCountry());

        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertTrue(foundUser.isPresent());
        assertEquals("123 Test St", foundUser.get().getStreet());
        assertEquals("98765", foundUser.get().getPostalCode());
        assertEquals("Testville", foundUser.get().getCity());
        assertEquals("Testland", foundUser.get().getCountry());
        assertEquals("fullPass", foundUser.get().getPasswordHash());
    }
}
