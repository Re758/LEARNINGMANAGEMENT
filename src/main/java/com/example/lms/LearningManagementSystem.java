package com.example.lms;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import java.sql.Date;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class LearningManagementSystem extends Application {

    // Database connection details
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/LMS";
    private static final String DB_USER = "LMS_USER";
    private static final String DB_PASSWORD = "123456"; // Replace with your PostgreSQL password

    // UI Components
    private Stage primaryStage;
    private BorderPane rootLayout;
    private Connection connection;
    private String currentUser;
    private String currentRole;
    private String currentTheme = "Light"; // Default theme
    private double passThreshold = 70.0; // Default passing grade (%)
    private boolean emailNotifications = true; // Default notification setting
    private String backupSchedule = "Daily"; // Default backup schedule

    // Sample data
    private List<String> courses = new ArrayList<>();
    private List<String> students = new ArrayList<>();

    // Online image URLs
    private static final String BACKGROUND_IMAGE_URL = "https://images.unsplash.com/photo-1509062522246-3755977927d7";
    private static final String LOGO_URL = "https://cdn-icons-png.flaticon.com/512/3419/3419097.png";
    private static final String USER_ICON_URL = "https://cdn-icons-png.flaticon.com/512/1077/1077114.png";
    private static final String COURSE_ICON_URL = "https://cdn-icons-png.flaticon.com/512/2436/2436637.png";
    private static final String PROGRESS_ICON_URL = "https://cdn-icons-png.flaticon.com/512/1828/1828961.png";
    private static final String NOTIFICATION_ICON_URL = "https://cdn-icons-png.flaticon.com/512/733/733635.png";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Learning Management System");

        // Set application icon
        try {
            Image logo = new Image(LOGO_URL);
            this.primaryStage.getIcons().add(logo);
        } catch (Exception e) {
            System.err.println("Error loading logo: " + e.getMessage());
        }

        try {
            initializeDatabase();
            loadSampleData();
            showWelcomePage();
            logActivity("System started");
        } catch (Exception e) {
            showAlert("Startup Error", "Failed to initialize application: " + e.getMessage());
            e.printStackTrace();
            primaryStage.close();
        }
    }

    private Image loadImage(String url) {
        try {
            return new Image(url);
        } catch (Exception e) {
            System.err.println("Failed to load image: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            createTablesIfNotExist();
            ensureDefaultAdmin();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private void createTablesIfNotExist() throws SQLException {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id SERIAL PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password VARCHAR(100) NOT NULL, " +
                "email VARCHAR(100) UNIQUE NOT NULL, " +
                "role VARCHAR(20) NOT NULL)";

        String createCoursesTable = "CREATE TABLE IF NOT EXISTS courses (" +
                "id SERIAL PRIMARY KEY, " +
                "title VARCHAR(100) NOT NULL, " +
                "description TEXT, " +
                "instructor_id INTEGER REFERENCES users(id), " +
                "approved BOOLEAN DEFAULT FALSE)";

        String createEnrollmentsTable = "CREATE TABLE IF NOT EXISTS enrollments (" +
                "id SERIAL PRIMARY KEY, " +
                "student_id INTEGER REFERENCES users(id), " +
                "course_id INTEGER REFERENCES courses(id), " +
                "progress INTEGER DEFAULT 0, " +
                "enrolled_date DATE, " +
                "UNIQUE(student_id, course_id))";

        String createMaterialsTable = "CREATE TABLE IF NOT EXISTS materials (" +
                "id SERIAL PRIMARY KEY, " +
                "course_id INTEGER REFERENCES courses(id), " +
                "title VARCHAR(100) NOT NULL, " +
                "content TEXT, " +
                "upload_date DATE, " +
                "file_path VARCHAR(255))";

        String createAssignmentsTable = "CREATE TABLE IF NOT EXISTS assignments (" +
                "id SERIAL PRIMARY KEY, " +
                "course_id INTEGER REFERENCES courses(id), " +
                "title VARCHAR(100) NOT NULL, " +
                "description TEXT, " +
                "deadline DATE)";

        String createStudentAssignmentsTable = "CREATE TABLE IF NOT EXISTS student_assignments (" +
                "id SERIAL PRIMARY KEY, " +
                "assignment_id INTEGER REFERENCES assignments(id), " +
                "student_id INTEGER REFERENCES users(id), " +
                "submission TEXT, " +
                "grade INTEGER, " +
                "feedback TEXT, " +
                "submitted_date DATE)";

        String createQuizzesTable = "CREATE TABLE IF NOT EXISTS quizzes (" +
                "id SERIAL PRIMARY KEY, " +
                "course_id INTEGER REFERENCES courses(id), " +
                "title VARCHAR(100) NOT NULL, " +
                "question TEXT NOT NULL, " +
                "options TEXT[], " +
                "correct_option INTEGER, " +
                "total_points INTEGER DEFAULT 100)";

        String createQuizSubmissionsTable = "CREATE TABLE IF NOT EXISTS quiz_submissions (" +
                "id SERIAL PRIMARY KEY, " +
                "quiz_id INTEGER REFERENCES quizzes(id), " +
                "student_id INTEGER REFERENCES users(id), " +
                "selected_option INTEGER, " +
                "score INTEGER, " +
                "submitted_date TIMESTAMP)";

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id SERIAL PRIMARY KEY, " +
                "sender_id INTEGER REFERENCES users(id), " +
                "receiver_id INTEGER REFERENCES users(id), " +
                "course_id INTEGER REFERENCES courses(id), " +
                "content TEXT NOT NULL, " +
                "sent_time TIMESTAMP, " +
                "is_read BOOLEAN DEFAULT FALSE)";

        String createLogsTable = "CREATE TABLE IF NOT EXISTS logs (" +
                "id SERIAL PRIMARY KEY, " +
                "user_id INTEGER REFERENCES users(id), " +
                "activity VARCHAR(200) NOT NULL, " +
                "timestamp TIMESTAMP)";

        String createNotificationsTable = "CREATE TABLE IF NOT EXISTS notifications (" +
                "id SERIAL PRIMARY KEY, " +
                "user_id INTEGER REFERENCES users(id), " +
                "content VARCHAR(200) NOT NULL, " +
                "type VARCHAR(50) NOT NULL, " +
                "created_at TIMESTAMP, " +
                "is_read BOOLEAN DEFAULT FALSE)";

        String createHelpMessagesTable = "CREATE TABLE IF NOT EXISTS help_messages (" +
                "id SERIAL PRIMARY KEY, " +
                "user_id INTEGER REFERENCES users(id), " +
                "message TEXT NOT NULL, " +
                "created_at TIMESTAMP, " +
                "status VARCHAR(20) DEFAULT 'Pending')";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createCoursesTable);
            stmt.execute(createEnrollmentsTable);
            stmt.execute(createMaterialsTable);
            stmt.execute(createAssignmentsTable);
            stmt.execute(createStudentAssignmentsTable);
            stmt.execute(createQuizzesTable);
            stmt.execute(createQuizSubmissionsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createLogsTable);
            stmt.execute(createNotificationsTable);
            stmt.execute(createHelpMessagesTable);
        }
    }

    private void ensureDefaultAdmin() throws SQLException {
        String insertAdmin = "INSERT INTO users (username, password, email, role) " +
                "VALUES ('admin', 'admin123', 'admin@lms.com', 'Admin') " +
                "ON CONFLICT (username) DO NOTHING";
        String updateAdmin = "UPDATE users SET password = 'admin123', email = 'admin@lms.com', role = 'Admin' WHERE username = 'admin'";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(insertAdmin);
            stmt.executeUpdate(updateAdmin);
        }
        logActivity("Default admin user ensured");
    }

    private void loadSampleData() {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM courses");
            rs.next();
            if (rs.getInt(1) == 0) {
                String insertCourses = "INSERT INTO courses (title, description, approved) VALUES " +
                        "('Mathematics 101', 'Intro to Algebra', TRUE), " +
                        "('Physics 101', 'Mechanics Basics', TRUE), " +
                        "('Computer Science 101', 'Programming Fundamentals', TRUE)";
                stmt.executeUpdate(insertCourses);
            }
        } catch (SQLException e) {
            System.err.println("Error loading sample courses: " + e.getMessage());
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT title FROM courses")) {
            courses.clear();
            while (rs.next()) {
                courses.add(rs.getString("title"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching courses: " + e.getMessage());
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username FROM users WHERE role = 'Student'")) {
            students.clear();
            while (rs.next()) {
                students.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching students: " + e.getMessage());
        }
    }

    private void logActivity(String activity) {
        if (connection == null || currentUser == null) return;
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO logs (user_id, activity, timestamp) VALUES ((SELECT id FROM users WHERE username = ?), ?, ?)")) {
            stmt.setString(1, currentUser);
            stmt.setString(2, activity);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging activity: " + e.getMessage());
        }
    }

    private void showWelcomePage() {
        StackPane mainLayout = new StackPane();
        mainLayout.setAlignment(Pos.TOP_CENTER);

        // Background image with overlay
        ImageView background = new ImageView(loadImage(BACKGROUND_IMAGE_URL));
        background.setFitWidth(900);
        background.setFitHeight(700);
        background.setOpacity(0.3);

        VBox contentLayout = new VBox(20);
        contentLayout.setAlignment(Pos.TOP_CENTER);
        contentLayout.setPadding(new Insets(20));
        contentLayout.setStyle("-fx-background-color: transparent;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(10));
        header.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2c3e50;" : "-fx-background-color: #4a6a8a;");

        ImageView logo = new ImageView(loadImage(LOGO_URL));
        logo.setFitWidth(50);
        logo.setFitHeight(50);

        Label headerLabel = new Label("Learning Management System");
        headerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.WHITE);

        header.getChildren().addAll(logo, headerLabel);

        Label tagline = new Label("Empowering Education with Seamless Course Management");
        tagline.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        tagline.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEGRAY : Color.LIGHTGRAY);

        VBox aboutBox = createSectionBox(
                "About Our LMS",
                "Manage courses, track progress, and engage learners with intuitive tools.",
                COURSE_ICON_URL
        );

        VBox featuresBox = createSectionBox(
                "Why Choose Us?",
                "• Comprehensive Course Management\n• Real-Time Progress Tracking\n• Secure Role-Based Access",
                PROGRESS_ICON_URL
        );

        HBox buttonPanel = new HBox(20);
        buttonPanel.setAlignment(Pos.CENTER);
        buttonPanel.setPadding(new Insets(20));

        Button loginButton = new Button("Login");
        loginButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        loginButton.setEffect(new DropShadow(10, Color.DARKGREEN));
        ImageView loginIcon = new ImageView(loadImage(USER_ICON_URL));
        loginIcon.setFitWidth(20);
        loginIcon.setFitHeight(20);
        loginButton.setGraphic(loginIcon);
        FadeTransition fade = new FadeTransition(Duration.seconds(2), loginButton);
        fade.setFromValue(1.0);
        fade.setToValue(0.3);
        fade.setCycleCount(FadeTransition.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();
        loginButton.setOnAction(e -> showLoginDialog());

        Button signupButton = new Button("Sign Up");
        signupButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");
        signupButton.setEffect(new DropShadow(10, Color.DARKBLUE));
        ImageView signupIcon = new ImageView(loadImage(USER_ICON_URL));
        signupIcon.setFitWidth(20);
        signupIcon.setFitHeight(20);
        signupButton.setGraphic(signupIcon);
        signupButton.setOnAction(e -> showSignupDialog());

        buttonPanel.getChildren().addAll(loginButton, signupButton);

        contentLayout.getChildren().addAll(header, tagline, aboutBox, featuresBox, buttonPanel);
        mainLayout.getChildren().addAll(background, contentLayout);

        Scene scene = new Scene(mainLayout, 900, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createSectionBox(String title, String content, String imageUrl) {
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        Text contentText = new Text(content);
        contentText.setFont(Font.font("Arial", 14));
        contentText.setWrappingWidth(600);
        contentText.setFill(currentTheme.equals("Light") ? Color.BLACK : Color.WHITE);

        ImageView imageView = new ImageView(loadImage(imageUrl));
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);

        HBox contentBox = new HBox(20);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        contentBox.getChildren().addAll(imageView, contentText);

        VBox sectionBox = new VBox(10);
        sectionBox.setAlignment(Pos.TOP_LEFT);
        sectionBox.setPadding(new Insets(15));
        sectionBox.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #ffffff; -fx-border-color: #ddd;" : "-fx-background-color: #4a6a8a; -fx-border-color: #666;");
        sectionBox.getChildren().addAll(titleLabel, contentBox);

        return sectionBox;
    }

    private void showLoginDialog() {
        Dialog<Triple<String, String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your credentials");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        TextField passwordText = new TextField();
        passwordText.setPromptText("Password");
        passwordText.setVisible(false);
        CheckBox showPassword = new CheckBox("Show Password");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("Student", "Instructor", "Admin");
        roleCombo.setPromptText("Select Role");
        Hyperlink forgotPassword = new Hyperlink("Forgot Password?");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(passwordText, 1, 1);
        grid.add(showPassword, 1, 2);
        grid.add(new Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);
        grid.add(forgotPassword, 1, 4);

        showPassword.setOnAction(e -> {
            if (showPassword.isSelected()) {
                passwordText.setText(passwordField.getText());
                passwordText.setVisible(true);
                passwordField.setVisible(false);
            } else {
                passwordField.setText(passwordText.getText());
                passwordText.setVisible(false);
                passwordField.setVisible(true);
            }
        });

        forgotPassword.setOnAction(e -> showResetPasswordDialog());

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                String password = showPassword.isSelected() ? passwordText.getText() : passwordField.getText();
                return new Triple<>(username.getText(), password, roleCombo.getValue());
            }
            return null;
        });

        Optional<Triple<String, String, String>> result = dialog.showAndWait();

        result.ifPresent(credentials -> {
            try {
                if (authenticateUser(credentials.getFirst(), credentials.getSecond(), credentials.getThird())) {
                    currentUser = credentials.getFirst();
                    currentRole = credentials.getThird();
                    logActivity("Logged in as " + currentRole);
                    showMainApplication();
                } else {
                    showAlert("Login Failed", "Invalid username, password, or role.");
                }
            } catch (SQLException e) {
                showAlert("Database Error", "Failed to authenticate user: " + e.getMessage());
            }
        });
    }

    private void showResetPasswordDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset your password");

        ButtonType resetButtonType = new ButtonType("Reset", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField emailField = new TextField();
        emailField.setPromptText("Email");

        grid.add(new Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == resetButtonType) {
                return emailField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(email -> {
            try {
                resetPasswordByEmail(email, "default123");
                showAlert("Success", "Password reset to 'default123'. Please login and change it.");
                logActivity("Password reset requested for email: " + email);
            } catch (SQLException e) {
                showAlert("Error", "Failed to reset password: " + e.getMessage());
            }
        });
    }

    private void resetPasswordByEmail(String email, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password = ? WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, email);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("No user found with email: " + email);
            }
        }
    }

    private boolean authenticateUser(String username, String password, String role) throws SQLException {
        String sql = "SELECT password, role FROM users WHERE username = ? AND role = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, role);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
        }
        return false;
    }

    private void showSignupDialog() {
        Dialog<Quad<String, String, String, String>> dialog = new Dialog<>();
        dialog.setTitle("Sign Up");
        dialog.setHeaderText("Create a new account");

        ButtonType signupButtonType = new ButtonType("Sign Up", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signupButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        TextField email = new TextField();
        email.setPromptText("Email");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("Student", "Instructor");
        roleCombo.setPromptText("Select Role");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);
        grid.add(new Label("Email:"), 0, 2);
        grid.add(email, 1, 2);
        grid.add(new Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == signupButtonType) {
                return new Quad<>(username.getText(), password.getText(), email.getText(), roleCombo.getValue());
            }
            return null;
        });

        Optional<Quad<String, String, String, String>> result = dialog.showAndWait();

        result.ifPresent(data -> {
            try {
                registerUser(data.getFirst(), data.getSecond(), data.getThird(), data.getFourth());
                showAlert("Success", "Account created successfully. Please login.");
                logActivity("New user registered: " + data.getFirst());
                if ("Admin".equals(currentRole)) {
                    addNotification(getUserIdByUsername(currentUser), "New user registered: " + data.getFirst(), "User");
                }
            } catch (SQLException e) {
                showAlert("Registration Failed", "Failed to create account: " + e.getMessage());
            }
        });
    }

    private void registerUser(String username, String password, String email, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, email);
            stmt.setString(4, role);
            stmt.executeUpdate();
        }
    }

    private void addNotification(int userId, String content, String type) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, content, type, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, content);
            stmt.setString(3, type);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }

    private void showMainApplication() {
        rootLayout = new BorderPane();
        rootLayout.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        MenuBar menuBar = createMenuBar();
        rootLayout.setTop(menuBar);

        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2c3e50;" : "-fx-background-color: #4a6a8a;");

        Label statusLabel = new Label("Logged in as: " + currentUser + " (" + currentRole + ")");
        statusLabel.setTextFill(Color.WHITE);
        Button logoutButton = new Button("Logout");
        logoutButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        logoutButton.setEffect(new DropShadow(5, Color.DARKRED));
        logoutButton.setOnAction(e -> {
            logActivity("Logged out");
            currentUser = null;
            currentRole = null;
            showWelcomePage();
        });

        statusBar.getChildren().addAll(statusLabel, new Label(" | "), logoutButton);
        rootLayout.setBottom(statusBar);

        switch (currentRole) {
            case "Admin":
                showAdminPanel();
                break;
            case "Instructor":
                showInstructorDashboard();
                break;
            case "Student":
                showStudentDashboard();
                break;
        }

        Scene scene = new Scene(rootLayout, 1000, 700);
        primaryStage.setScene(scene);
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().add(exitItem);

        Menu viewMenu = new Menu("View");
        MenuItem dashboardItem = new MenuItem("Dashboard");
        dashboardItem.setOnAction(e -> {
            switch (currentRole) {
                case "Admin":
                    showAdminPanel();
                    break;
                case "Instructor":
                    showInstructorDashboard();
                    break;
                case "Student":
                    showStudentDashboard();
                    break;
            }
        });
        MenuItem coursesItem = new MenuItem("Courses");
        coursesItem.setOnAction(e -> showCourses());
        viewMenu.getItems().addAll(dashboardItem, coursesItem);

        if ("Admin".equals(currentRole) || "Instructor".equals(currentRole)) {
            MenuItem studentsItem = new MenuItem("Students");
            studentsItem.setOnAction(e -> showStudents());
            viewMenu.getItems().add(studentsItem);
        }

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        MenuItem helpItem = new MenuItem("Contact Admin");
        helpItem.setOnAction(e -> showHelpDialog());
        helpMenu.getItems().addAll(aboutItem, helpItem);

        if ("Admin".equals(currentRole)) {
            Menu adminMenu = new Menu("Admin");
            MenuItem manageUsersItem = new MenuItem("Manage Users");
            manageUsersItem.setOnAction(e -> showAdminPanel());
            MenuItem reportsItem = new MenuItem("Reports");
            reportsItem.setOnAction(e -> showReports());
            MenuItem settingsItem = new MenuItem("Settings");
            settingsItem.setOnAction(e -> showSettings());
            MenuItem notificationsItem = new MenuItem("Notifications");
            notificationsItem.setOnAction(e -> showNotifications());
            MenuItem helpMessagesItem = new MenuItem("Help Messages");
            helpMessagesItem.setOnAction(e -> showHelpMessages());
            adminMenu.getItems().addAll(manageUsersItem, reportsItem, settingsItem, notificationsItem, helpMessagesItem);
            menuBar.getMenus().add(adminMenu);
        }

        if ("Student".equals(currentRole)) {
            Menu studentMenu = new Menu("Notifications");
            MenuItem notificationsItem = new MenuItem("View Notifications");
            notificationsItem.setOnAction(e -> showNotifications());
            studentMenu.getItems().add(notificationsItem);
            menuBar.getMenus().add(studentMenu);
        }

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    private void showHelpDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Contact Admin");
        dialog.setHeaderText("Send a message to the administrator");

        ButtonType sendButtonType = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButtonType, ButtonType.CANCEL);

        TextArea messageArea = new TextArea();
        messageArea.setPromptText("Enter your message here...");
        messageArea.setPrefRowCount(5);

        dialog.getDialogPane().setContent(new VBox(10, new Label("Message:"), messageArea));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == sendButtonType) {
                return messageArea.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(message -> {
            try {
                sendHelpMessage(message);
                showAlert("Success", "Your message has been sent to the admin.");
                logActivity("Sent help message to admin");
            } catch (SQLException e) {
                showAlert("Error", "Failed to send message: " + e.getMessage());
            }
        });
    }

    private void sendHelpMessage(String message) throws SQLException {
        String sql = "INSERT INTO help_messages (user_id, message, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            stmt.setString(2, message);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }

    private void showHelpMessages() {
        VBox helpPane = new VBox(10);
        helpPane.setPadding(new Insets(20));
        helpPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Help Messages");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<HelpMessage> messageTable = new TableView<>();
        TableColumn<HelpMessage, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(cellData -> new SimpleStringProperty(getUsernameById(cellData.getValue().userId)));
        TableColumn<HelpMessage, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().message));
        TableColumn<HelpMessage, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().createdAt.toString()));
        TableColumn<HelpMessage, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status));
        messageTable.getColumns().addAll(userCol, messageCol, dateCol, statusCol);
        messageTable.setItems(FXCollections.observableArrayList(getHelpMessages()));

        Button resolveButton = new Button("Mark as Resolved");
        resolveButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        resolveButton.setOnAction(e -> {
            HelpMessage selected = messageTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    markHelpMessageResolved(selected.id);
                    messageTable.setItems(FXCollections.observableArrayList(getHelpMessages()));
                    logActivity("Marked help message as resolved");
                } catch (SQLException ex) {
                    showAlert("Error", "Failed to update message status: " + ex.getMessage());
                }
            }
        });

        helpPane.getChildren().addAll(titleLabel, messageTable, resolveButton);
        rootLayout.setCenter(helpPane);
    }

    private List<HelpMessage> getHelpMessages() {
        List<HelpMessage> messages = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, user_id, message, created_at, status FROM help_messages ORDER BY created_at DESC")) {
            while (rs.next()) {
                messages.add(new HelpMessage(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("message"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching help messages: " + e.getMessage());
        }
        return messages;
    }

    private void markHelpMessageResolved(int id) throws SQLException {
        String sql = "UPDATE help_messages SET status = 'Resolved' WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private void showAdminPanel() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Tab userTab = new Tab("User Management");
        userTab.setClosable(false);
        userTab.setContent(createUserManagementTab());
        Tab courseTab = new Tab("Course Management");
        courseTab.setClosable(false);
        courseTab.setContent(createCourseManagementTab());
        Tab dataTab = new Tab("Data Management");
        dataTab.setClosable(false);
        dataTab.setContent(createDataManagementTab());
        Tab notificationsTab = new Tab("Notifications");
        notificationsTab.setClosable(false);
        notificationsTab.setContent(createNotificationsTab());

        tabPane.getTabs().addAll(userTab, courseTab, dataTab, notificationsTab);
        rootLayout.setCenter(tabPane);
        logActivity("Accessed admin panel");
    }

    private VBox createUserManagementTab() {
        VBox userPane = new VBox(10);
        userPane.setPadding(new Insets(20));
        userPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Manage Users");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<User> userTable = new TableView<>();
        TableColumn<User, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().username));
        TableColumn<User, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email));
        TableColumn<User, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().role));
        userTable.getColumns().addAll(usernameCol, emailCol, roleCol);
        userTable.setItems(FXCollections.observableArrayList(getAllUsers()));

        GridPane userForm = new GridPane();
        userForm.setHgap(10);
        userForm.setVgap(10);
        userForm.setPadding(new Insets(10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("Student", "Instructor", "Admin");
        roleCombo.setPromptText("Select Role");

        userForm.add(new Label("Username:"), 0, 0);
        userForm.add(usernameField, 1, 0);
        userForm.add(new Label("Password:"), 0, 1);
        userForm.add(passwordField, 1, 1);
        userForm.add(new Label("Email:"), 0, 2);
        userForm.add(emailField, 1, 2);
        userForm.add(new Label("Role:"), 0, 3);
        userForm.add(roleCombo, 1, 3);

        Button addButton = new Button("Add User");
        addButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button updateButton = new Button("Update User");
        updateButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");
        Button deleteButton = new Button("Delete User");
        deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        Button resetPasswordButton = new Button("Reset Password");
        resetPasswordButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #FFC107; -fx-text-fill: white;" : "-fx-background-color: #FFCA28; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        addButton.setOnAction(e -> {
            try {
                if (usernameField.getText().isEmpty() || passwordField.getText().isEmpty() || emailField.getText().isEmpty() || roleCombo.getValue() == null) {
                    statusLabel.setText("All fields are required.");
                    statusLabel.setTextFill(Color.RED);
                    return;
                }
                registerUser(usernameField.getText(), passwordField.getText(), emailField.getText(), roleCombo.getValue());
                userTable.setItems(FXCollections.observableArrayList(getAllUsers()));
                statusLabel.setText("User added successfully!");
                logActivity("Added user: " + usernameField.getText());
                if ("Admin".equals(currentRole)) {
                    addNotification(getUserIdByUsername(currentUser), "New user added: " + usernameField.getText(), "User");
                }
                usernameField.clear();
                passwordField.clear();
                emailField.clear();
                roleCombo.setValue(null);
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        updateButton.setOnAction(e -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    updateUser(selected.id, usernameField.getText(), passwordField.getText(), emailField.getText(), roleCombo.getValue());
                    userTable.setItems(FXCollections.observableArrayList(getAllUsers()));
                    statusLabel.setText("User updated successfully!");
                    logActivity("Updated user: " + usernameField.getText());
                    if ("Admin".equals(currentRole)) {
                        addNotification(getUserIdByUsername(currentUser), "User updated: " + usernameField.getText(), "User");
                    }
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        deleteButton.setOnAction(e -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    deleteUser(selected.id);
                    userTable.setItems(FXCollections.observableArrayList(getAllUsers()));
                    statusLabel.setText("User deleted successfully!");
                    logActivity("Deleted user: " + selected.username);
                    if ("Admin".equals(currentRole)) {
                        addNotification(getUserIdByUsername(currentUser), "User deleted: " + selected.username, "User");
                    }
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        resetPasswordButton.setOnAction(e -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    resetPassword(selected.id, "default123");
                    statusLabel.setText("Password reset to 'default123'!");
                    logActivity("Reset password for user: " + selected.username);
                    if ("Admin".equals(currentRole)) {
                        addNotification(getUserIdByUsername(currentUser), "Password reset for user: " + selected.username, "User");
                    }
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        userTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                usernameField.setText(newValue.username);
                emailField.setText(newValue.email);
                roleCombo.setValue(newValue.role);
            }
        });

        userPane.getChildren().addAll(titleLabel, userTable, userForm, new HBox(10, addButton, updateButton, deleteButton, resetPasswordButton), statusLabel);
        return userPane;
    }

    private List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, username, email, role FROM users")) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("email"), rs.getString("role")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching users: " + e.getMessage());
        }
        return users;
    }

    private void updateUser(int id, String username, String password, String email, String role) throws SQLException {
        String sql = "UPDATE users SET username = ?, password = ?, email = ?, role = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password.isEmpty() ? getUserPassword(id) : password);
            stmt.setString(3, email);
            stmt.setString(4, role);
            stmt.setInt(5, id);
            stmt.executeUpdate();
        }
    }

    private String getUserPassword(int id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT password FROM users WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("password");
        }
        return "";
    }

    private void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private void resetPassword(int id, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
    }

    private VBox createCourseManagementTab() {
        VBox coursePane = new VBox(10);
        coursePane.setPadding(new Insets(20));
        coursePane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Manage Courses");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<Course> courseTable = new TableView<>();
        TableColumn<Course, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Course, String> instructorCol = new TableColumn<>("Instructor");
        instructorCol.setCellValueFactory(cellData -> new SimpleStringProperty(getUsernameById(cellData.getValue().instructorId)));
        TableColumn<Course, String> approvedCol = new TableColumn<>("Approved");
        approvedCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().approved ? "Yes" : "No"));
        TableColumn<Course, String> enrollmentCol = new TableColumn<>("Enrollments");
        enrollmentCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(getEnrollmentCount(cellData.getValue().id))));
        courseTable.getColumns().addAll(titleCol, instructorCol, approvedCol, enrollmentCol);
        courseTable.setItems(FXCollections.observableArrayList(getAllCourses()));

        GridPane courseForm = new GridPane();
        courseForm.setHgap(10);
        courseForm.setVgap(10);
        courseForm.setPadding(new Insets(10));

        TextField titleField = new TextField();
        titleField.setPromptText("Course Title");
        TextArea descField = new TextArea();
        descField.setPromptText("Description");
        descField.setPrefRowCount(3);
        ComboBox<String> instructorCombo = new ComboBox<>();
        instructorCombo.setPromptText("Select Instructor");
        instructorCombo.getItems().addAll(getInstructors());
        CheckBox approvedCheck = new CheckBox("Approved");

        courseForm.add(new Label("Title:"), 0, 0);
        courseForm.add(titleField, 1, 0);
        courseForm.add(new Label("Description:"), 0, 1);
        courseForm.add(descField, 1, 1);
        courseForm.add(new Label("Instructor:"), 0, 2);
        courseForm.add(instructorCombo, 1, 2);
        courseForm.add(new Label("Approved:"), 0, 3);
        courseForm.add(approvedCheck, 1, 3);

        Button addButton = new Button("Add Course");
        addButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button updateButton = new Button("Update Course");
        updateButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");
        Button deleteButton = new Button("Delete Course");
        deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        addButton.setOnAction(e -> {
            try {
                if (titleField.getText().isEmpty() || instructorCombo.getValue() == null) {
                    statusLabel.setText("Title and instructor are required.");
                    statusLabel.setTextFill(Color.RED);
                    return;
                }
                addCourse(titleField.getText(), descField.getText(), getUserIdByUsername(instructorCombo.getValue()), approvedCheck.isSelected());
                courseTable.setItems(FXCollections.observableArrayList(getAllCourses()));
                statusLabel.setText("Course added successfully!");
                logActivity("Added course: " + titleField.getText());
                if ("Admin".equals(currentRole)) {
                    addNotification(getUserIdByUsername(currentUser), "New course added: " + titleField.getText(), "Course");
                }
                titleField.clear();
                descField.clear();
                instructorCombo.setValue(null);
                approvedCheck.setSelected(false);
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        updateButton.setOnAction(e -> {
            Course selected = courseTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    if (titleField.getText().isEmpty() || instructorCombo.getValue() == null) {
                        statusLabel.setText("Title and instructor are required.");
                        statusLabel.setTextFill(Color.RED);
                        return;
                    }
                    updateCourse(selected.id, titleField.getText(), descField.getText(), getUserIdByUsername(instructorCombo.getValue()), approvedCheck.isSelected());
                    courseTable.setItems(FXCollections.observableArrayList(getAllCourses()));
                    statusLabel.setText("Course updated successfully!");
                    logActivity("Updated course: " + titleField.getText());
                    if ("Admin".equals(currentRole)) {
                        addNotification(getUserIdByUsername(currentUser), "Course updated: " + titleField.getText(), "Course");
                    }
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        deleteButton.setOnAction(e -> {
            Course selected = courseTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    deleteCourse(selected.id);
                    courseTable.setItems(FXCollections.observableArrayList(getAllCourses()));
                    statusLabel.setText("Course deleted successfully!");
                    logActivity("Deleted course: " + selected.title);
                    if ("Admin".equals(currentRole)) {
                        addNotification(getUserIdByUsername(currentUser), "Course deleted: " + selected.title, "Course");
                    }
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        courseTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                titleField.setText(newValue.title);
                descField.setText(newValue.description);
                instructorCombo.setValue(getUsernameById(newValue.instructorId));
                approvedCheck.setSelected(newValue.approved);
            }
        });

        coursePane.getChildren().addAll(titleLabel, courseTable, courseForm, new HBox(10, addButton, updateButton, deleteButton), statusLabel);
        return coursePane;
    }

    private int getEnrollmentCount(int courseId) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM enrollments WHERE course_id = ?")) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error fetching enrollment count: " + e.getMessage());
        }
        return 0;
    }

    private List<Course> getAllCourses() {
        List<Course> courses = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, title, description, instructor_id, approved FROM courses")) {
            while (rs.next()) {
                courses.add(new Course(rs.getInt("id"), rs.getString("title"), rs.getString("description"), rs.getInt("instructor_id"), rs.getBoolean("approved")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching courses: " + e.getMessage());
        }
        return courses;
    }

    private List<String> getInstructors() {
        List<String> instructors = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username FROM users WHERE role = 'Instructor'")) {
            while (rs.next()) {
                instructors.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching instructors: " + e.getMessage());
        }
        return instructors;
    }

    private String getUsernameById(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT username FROM users WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("username");
        } catch (SQLException e) {
            System.err.println("Error fetching username: " + e.getMessage());
        }
        return "None";
    }

    private int getUserIdByUsername(String username) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            System.err.println("Error fetching user ID: " + e.getMessage());
        }
        return -1;
    }

    private void addCourse(String title, String description, int instructorId, boolean approved) throws SQLException {
        String sql = "INSERT INTO courses (title, description, instructor_id, approved) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setInt(3, instructorId);
            stmt.setBoolean(4, approved);
            stmt.executeUpdate();
        }
    }

    private void updateCourse(int id, String title, String description, int instructorId, boolean approved) throws SQLException {
        String sql = "UPDATE courses SET title = ?, description = ?, instructor_id = ?, approved = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setInt(3, instructorId);
            stmt.setBoolean(4, approved);
            stmt.setInt(5, id);
            stmt.executeUpdate();
        }
    }

    private void deleteCourse(int id) throws SQLException {
        String sql = "DELETE FROM courses WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private VBox createDataManagementTab() {
        VBox dataPane = new VBox(10);
        dataPane.setPadding(new Insets(20));
        dataPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Data Management");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        Button backupButton = new Button("Backup Database");
        backupButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button restoreButton = new Button("Restore Database");
        restoreButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");
        Button checkConnButton = new Button("Check Connectivity");
        checkConnButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #FFC107; -fx-text-fill: white;" : "-fx-background-color: #FFCA28; -fx-text-fill: white;");
        Button viewLogsButton = new Button("View Activity Logs");
        viewLogsButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #9C27B0; -fx-text-fill: white;" : "-fx-background-color: #AB47BC; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        backupButton.setOnAction(e -> {
            try {
                backupDatabase();
                statusLabel.setText("Backup created successfully!");
                logActivity("Database backed up");
            } catch (IOException | SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        restoreButton.setOnAction(e -> {
            try {
                restoreDatabase();
                statusLabel.setText("Database restored successfully!");
                logActivity("Database restored");
            } catch (IOException | SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        checkConnButton.setOnAction(e -> {
            try {
                connection.createStatement().execute("SELECT 1");
                statusLabel.setText("Database connection is active!");
                logActivity("Checked database connectivity");
            } catch (SQLException ex) {
                statusLabel.setText("Connection failed: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        viewLogsButton.setOnAction(e -> {
            showActivityLogs();
            logActivity("Viewed activity logs");
        });

        dataPane.getChildren().addAll(titleLabel, new HBox(10, backupButton, restoreButton, checkConnButton, viewLogsButton), statusLabel);
        return dataPane;
    }

    private void backupDatabase() throws IOException, SQLException {
        String backupFile = "lms_backup_" + LocalDate.now() + ".sql";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            try (PrintWriter writer = new PrintWriter(backupFile)) {
                while (rs.next()) {
                    String table = rs.getString("table_name");
                    writer.println("COPY " + table + " TO STDOUT;");
                    try (ResultSet data = stmt.executeQuery("SELECT * FROM " + table)) {
                        while (data.next()) {
                            for (int i = 1; i <= data.getMetaData().getColumnCount(); i++) {
                                writer.print((data.getString(i) != null ? data.getString(i) : "NULL") + "\t");
                            }
                            writer.println();
                        }
                    }
                }
            }
        }
    }

    private void restoreDatabase() throws IOException, SQLException {
        showAlert("Restore", "Database restore simulated. Implement file picker for production.");
    }

    private void showActivityLogs() {
        VBox logPane = new VBox(10);
        logPane.setPadding(new Insets(20));
        logPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Activity Logs");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(400);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT u.username, l.activity, l.timestamp FROM logs l JOIN users u ON l.user_id = u.id ORDER BY l.timestamp DESC")) {
            StringBuilder logs = new StringBuilder();
            while (rs.next()) {
                logs.append(String.format("%s - %s: %s\n", rs.getTimestamp("timestamp"), rs.getString("username"), rs.getString("activity")));
            }
            logArea.setText(logs.toString());
        } catch (SQLException e) {
            logArea.setText("Error fetching logs: " + e.getMessage());
        }

        logPane.getChildren().addAll(titleLabel, logArea);
        rootLayout.setCenter(logPane);
    }

    private VBox createNotificationsTab() {
        VBox notifPane = new VBox(10);
        notifPane.setPadding(new Insets(20));
        notifPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Notifications");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        // Add unread count badge to title
        int unreadCount = getUnreadNotificationCount();
        if (unreadCount > 0) {
            titleLabel.setText(titleLabel.getText() + " (" + unreadCount + " unread)");
        }

        TableView<Notification> notifTable = new TableView<>();
        TableColumn<Notification, String> contentCol = new TableColumn<>("Content");
        contentCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().content));
        TableColumn<Notification, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().type));
        TableColumn<Notification, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().createdAt.toString()));
        TableColumn<Notification, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().isRead ? "Read" : "Unread"));
        notifTable.getColumns().addAll(contentCol, typeCol, dateCol, statusCol);
        notifTable.setItems(FXCollections.observableArrayList(getNotifications()));

        Button markReadButton = new Button("Mark as Read");
        markReadButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button clearButton = new Button("Clear All");
        clearButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        notifTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null && !newValue.isRead) {
                try {
                    markNotificationRead(newValue.id);
                    notifTable.setItems(FXCollections.observableArrayList(getNotifications()));
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        markReadButton.setOnAction(e -> {
            Notification selected = notifTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    markNotificationRead(selected.id);
                    notifTable.setItems(FXCollections.observableArrayList(getNotifications()));
                    statusLabel.setText("Notification marked as read!");
                    logActivity("Marked notification as read: " + selected.content);
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        clearButton.setOnAction(e -> {
            try {
                clearNotifications();
                notifTable.setItems(FXCollections.observableArrayList(getNotifications()));
                statusLabel.setText("All notifications cleared!");
                logActivity("Cleared all notifications");
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        notifPane.getChildren().addAll(titleLabel, notifTable, new HBox(10, markReadButton, clearButton), statusLabel);
        return notifPane;
    }

    private int getUnreadNotificationCount() {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching unread notification count: " + e.getMessage());
        }
        return 0;
    }

    private List<Notification> getNotifications() {
        List<Notification> notifications = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT id, content, type, created_at, is_read FROM notifications WHERE user_id = ? ORDER BY created_at DESC")) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                notifications.add(new Notification(
                        rs.getInt("id"),
                        rs.getString("content"),
                        rs.getString("type"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getBoolean("is_read")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching notifications: " + e.getMessage());
        }
        return notifications;
    }

    private void markNotificationRead(int id) throws SQLException {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    private void clearNotifications() throws SQLException {
        String sql = "DELETE FROM notifications WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            stmt.executeUpdate();
        }
    }

    private void showReports() {
        TabPane reportPane = new TabPane();
        reportPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Tab userReportTab = new Tab("User Distribution");
        userReportTab.setClosable(false);
        userReportTab.setContent(createUserReport());
        Tab courseReportTab = new Tab("Course Enrollments");
        courseReportTab.setClosable(false);
        courseReportTab.setContent(createCourseReport());
        Tab progressReportTab = new Tab("Student Progress");
        progressReportTab.setClosable(false);
        progressReportTab.setContent(createProgressReport());

        reportPane.getTabs().addAll(userReportTab, courseReportTab, progressReportTab);
        rootLayout.setCenter(reportPane);
        logActivity("Viewed reports");
    }

    private VBox createUserReport() {
        VBox reportPane = new VBox(10);
        reportPane.setPadding(new Insets(20));
        reportPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("User Distribution Report");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        PieChart chart = new PieChart();
        chart.setTitle("Users by Role");

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT role, COUNT(*) as count FROM users GROUP BY role")) {
            while (rs.next()) {
                chart.getData().add(new PieChart.Data(rs.getString("role"), rs.getInt("count")));
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to generate user report: " + e.getMessage());
        }

        reportPane.getChildren().addAll(titleLabel, chart);
        return reportPane;
    }

    private VBox createCourseReport() {
        VBox reportPane = new VBox(10);
        reportPane.setPadding(new Insets(20));
        reportPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Course Enrollment Report");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Enrollments per Course");
        xAxis.setLabel("Course");
        yAxis.setLabel("Number of Students");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Enrollments");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT c.title, COUNT(e.id) as count FROM courses c LEFT JOIN enrollments e ON c.id = e.course_id GROUP BY c.title")) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString("title"), rs.getInt("count")));
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to generate course report: " + e.getMessage());
        }
        chart.getData().add(series);

        reportPane.getChildren().addAll(titleLabel, chart);
        return reportPane;
    }

    private VBox createProgressReport() {
        VBox reportPane = new VBox(10);
        reportPane.setPadding(new Insets(20));
        reportPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Student Progress Report");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Average Progress per Course");
        xAxis.setLabel("Course");
        yAxis.setLabel("Average Progress (%)");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Progress");
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT c.title, AVG(e.progress) as avg_progress FROM enrollments e JOIN courses c ON e.course_id = c.id GROUP BY c.title")) {
            while (rs.next()) {
                series.getData().add(new XYChart.Data<>(rs.getString("title"), rs.getDouble("avg_progress")));
            }
        } catch (SQLException e) {
            showAlert("Error", "Failed to generate progress report: " + e.getMessage());
        }
        chart.getData().add(series);

        reportPane.getChildren().addAll(titleLabel, chart);
        return reportPane;
    }

    private void showSettings() {
        VBox settingsPane = new VBox(10);
        settingsPane.setPadding(new Insets(20));
        settingsPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("System Settings");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark");
        themeCombo.setValue(currentTheme);

        TextField passThresholdField = new TextField(String.valueOf(passThreshold));
        passThresholdField.setPromptText("Passing Threshold (%)");

        TextField deadlineField = new TextField("2025-12-31");
        deadlineField.setPromptText("Submission Deadline (YYYY-MM-DD)");

        CheckBox notificationCheck = new CheckBox("Enable Email Notifications");
        notificationCheck.setSelected(emailNotifications);

        ComboBox<String> backupScheduleCombo = new ComboBox<>();
        backupScheduleCombo.getItems().addAll("Daily", "Weekly", "Monthly");
        backupScheduleCombo.setValue(backupSchedule);

        Button saveButton = new Button("Save Settings");
        saveButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        saveButton.setOnAction(e -> {
            try {
                currentTheme = themeCombo.getValue();
                passThreshold = Double.parseDouble(passThresholdField.getText());
                LocalDate.parse(deadlineField.getText());
                emailNotifications = notificationCheck.isSelected();
                backupSchedule = backupScheduleCombo.getValue();
                showAlert("Success", "Settings saved!");
                logActivity("Updated settings: theme=" + currentTheme + ", threshold=" + passThreshold + ", deadline=" + deadlineField.getText() + ", notifications=" + emailNotifications + ", backup=" + backupSchedule);
                showMainApplication();
            } catch (Exception ex) {
                showAlert("Error", "Invalid input: " + ex.getMessage());
            }
        });

        settingsPane.getChildren().addAll(
                titleLabel,
                new HBox(10, new Label("Theme:"), themeCombo),
                new HBox(10, new Label("Passing Threshold (%):"), passThresholdField),
                new HBox(10, new Label("Submission Deadline:"), deadlineField),
                new HBox(10, new Label("Notifications:"), notificationCheck),
                new HBox(10, new Label("Backup Schedule:"), backupScheduleCombo),
                saveButton
        );
        rootLayout.setCenter(settingsPane);
    }

    private void showNotifications() {
        VBox notificationsPane = createNotificationsTab();
        rootLayout.setCenter(notificationsPane);
        logActivity("Viewed notifications");
    }

    private void showInstructorDashboard() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Tab overviewTab = new Tab("Overview");
        overviewTab.setClosable(false);
        overviewTab.setContent(createInstructorOverviewTab());
        Tab contentTab = new Tab("Course Content");
        contentTab.setClosable(false);
        contentTab.setContent(createInstructorContentTab());
        Tab gradingTab = new Tab("Grading");
        gradingTab.setClosable(false);
        gradingTab.setContent(createInstructorGradingTab());
        Tab commTab = new Tab("Communication");
        commTab.setClosable(false);
        commTab.setContent(createInstructorCommTab());

        tabPane.getTabs().addAll(overviewTab, contentTab, gradingTab, commTab);
        rootLayout.setCenter(tabPane);
        logActivity("Accessed instructor dashboard");
    }

    private VBox createInstructorOverviewTab() {
        VBox overviewPane = new VBox(10);
        overviewPane.setPadding(new Insets(20));
        overviewPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Instructor Overview");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<Course> courseTable = new TableView<>();
        TableColumn<Course, String> titleCol = new TableColumn<>("Course Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Course, String> enrollmentCol = new TableColumn<>("Students Enrolled");
        enrollmentCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(getEnrollmentCount(cellData.getValue().id))));
        courseTable.getColumns().addAll(titleCol, enrollmentCol);
        courseTable.setItems(FXCollections.observableArrayList(getAllCourses().stream()
                .filter(c -> c.instructorId == getUserIdByUsername(currentUser))
                .collect(Collectors.toList())));

        overviewPane.getChildren().addAll(titleLabel, courseTable);
        return overviewPane;
    }

    private VBox createInstructorContentTab() {
        VBox contentPane = new VBox(10);
        contentPane.setPadding(new Insets(20));
        contentPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Manage Course Content");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        ComboBox<String> courseCombo = new ComboBox<>();
        courseCombo.getItems().addAll(getInstructorCourses());
        courseCombo.setPromptText("Select Course");

        TextField materialTitle = new TextField();
        materialTitle.setPromptText("Material Title");
        TextArea materialContent = new TextArea();
        materialContent.setPromptText("Material Content");
        DatePicker deadlinePicker = new DatePicker();

        Button addMaterialButton = new Button("Add Material");
        addMaterialButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button addQuizButton = new Button("Add Quiz");
        addQuizButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");
        Button addAssignmentButton = new Button("Add Assignment");
        addAssignmentButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #FFC107; -fx-text-fill: white;" : "-fx-background-color: #FFCA28; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        addMaterialButton.setOnAction(e -> {
            try {
                if (courseCombo.getValue() == null || materialTitle.getText().isEmpty()) {
                    statusLabel.setText("Course and title are required.");
                    statusLabel.setTextFill(Color.RED);
                    return;
                }
                int courseId = getCourseIdByTitle(courseCombo.getValue());
                addMaterial(courseId, materialTitle.getText(), materialContent.getText(), deadlinePicker.getValue());
                statusLabel.setText("Material added successfully!");
                logActivity("Added material to course: " + courseCombo.getValue());
                notifyStudents(courseId, "New material added: " + materialTitle.getText());
                materialTitle.clear();
                materialContent.clear();
                deadlinePicker.setValue(null);
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        addQuizButton.setOnAction(e -> {
            try {
                if (courseCombo.getValue() == null) {
                    statusLabel.setText("Please select a course.");
                    statusLabel.setTextFill(Color.RED);
                    return;
                }
                int courseId = getCourseIdByTitle(courseCombo.getValue());
                showQuizCreationDialog(courseId);
                statusLabel.setText("Quiz added successfully!");
                logActivity("Added quiz to course: " + courseCombo.getValue());
                notifyStudents(courseId, "New quiz added to course: " + courseCombo.getValue());
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        addAssignmentButton.setOnAction(e -> {
            try {
                if (courseCombo.getValue() == null || materialTitle.getText().isEmpty() || deadlinePicker.getValue() == null) {
                    statusLabel.setText("Course, title, and deadline are required.");
                    statusLabel.setTextFill(Color.RED);
                    return;
                }
                int courseId = getCourseIdByTitle(courseCombo.getValue());
                addAssignment(courseId, materialTitle.getText(), deadlinePicker.getValue());
                statusLabel.setText("Assignment added successfully!");
                logActivity("Added assignment to course: " + courseCombo.getValue());
                notifyStudents(courseId, "New assignment added: " + materialTitle.getText());
                materialTitle.clear();
                materialContent.clear();
                deadlinePicker.setValue(null);
            } catch (SQLException ex) {
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        GridPane formPane = new GridPane();
        formPane.setHgap(10);
        formPane.setVgap(10);
        formPane.setPadding(new Insets(10));
        formPane.add(new Label("Course:"), 0, 0);
        formPane.add(courseCombo, 1, 0);
        formPane.add(new Label("Title:"), 0, 1);
        formPane.add(materialTitle, 1, 1);
        formPane.add(new Label("Content:"), 0, 2);
        formPane.add(materialContent, 1, 2);
        formPane.add(new Label("Deadline:"), 0, 3);
        formPane.add(deadlinePicker, 1, 3);

        HBox buttonPane = new HBox(10, addMaterialButton, addQuizButton, addAssignmentButton);
        buttonPane.setAlignment(Pos.CENTER);

        contentPane.getChildren().addAll(titleLabel, formPane, buttonPane, statusLabel);
        return contentPane;
    }

    private void showQuizCreationDialog(int courseId) throws SQLException {
        Dialog<Quiz> dialog = new Dialog<>();
        dialog.setTitle("Create Quiz");
        dialog.setHeaderText("Add a new quiz question");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField titleField = new TextField();
        titleField.setPromptText("Quiz Title");
        TextArea questionField = new TextArea();
        questionField.setPromptText("Question");
        questionField.setPrefRowCount(3);
        TextField option1 = new TextField();
        option1.setPromptText("Option 1");
        TextField option2 = new TextField();
        option2.setPromptText("Option 2");
        TextField option3 = new TextField();
        option3.setPromptText("Option 3");
        TextField option4 = new TextField();
        option4.setPromptText("Option 4");
        ComboBox<Integer> correctOption = new ComboBox<>();
        correctOption.getItems().addAll(1, 2, 3, 4);
        correctOption.setPromptText("Correct Option");
        TextField pointsField = new TextField("100");
        pointsField.setPromptText("Total Points");

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Question:"), 0, 1);
        grid.add(questionField, 1, 1);
        grid.add(new Label("Option 1:"), 0, 2);
        grid.add(option1, 1, 2);
        grid.add(new Label("Option 2:"), 0, 3);
        grid.add(option2, 1, 3);
        grid.add(new Label("Option 3:"), 0, 4);
        grid.add(option3, 1, 4);
        grid.add(new Label("Option 4:"), 0, 5);
        grid.add(option4, 1, 5);
        grid.add(new Label("Correct Option:"), 0, 6);
        grid.add(correctOption, 1, 6);
        grid.add(new Label("Total Points:"), 0, 7);
        grid.add(pointsField, 1, 7);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String[] options = {option1.getText(), option2.getText(), option3.getText(), option4.getText()};
                return new Quiz(0, courseId, titleField.getText(), questionField.getText(), options,
                        correctOption.getValue(), Integer.parseInt(pointsField.getText()));
            }
            return null;
        });

        Optional<Quiz> result = dialog.showAndWait();
        result.ifPresent(quiz -> {
            try {
                addQuiz(quiz);
            } catch (SQLException e) {
                showAlert("Error", "Failed to add quiz: " + e.getMessage());
            }
        });
    }

    private void addQuiz(Quiz quiz) throws SQLException {
        String sql = "INSERT INTO quizzes (course_id, title, question, options, correct_option, total_points) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, quiz.courseId);
            stmt.setString(2, quiz.title);
            stmt.setString(3, quiz.question);
            stmt.setArray(4, connection.createArrayOf("TEXT", quiz.options));
            stmt.setInt(5, quiz.correctOption);
            stmt.setInt(6, quiz.totalPoints);
            stmt.executeUpdate();
        }
    }

    private void addMaterial(int courseId, String title, String content, LocalDate deadline) throws SQLException {
        String sql = "INSERT INTO materials (course_id, title, content, upload_date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            stmt.setString(2, title);
            stmt.setString(3, content);
            stmt.setDate(4, deadline != null ? Date.valueOf(deadline) : Date.valueOf(LocalDate.now()));
            stmt.executeUpdate();
        }
    }

    private void addAssignment(int courseId, String title, LocalDate deadline) throws SQLException {
        String sql = "INSERT INTO assignments (course_id, title, deadline) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            stmt.setString(2, title);
            stmt.setDate(3, deadline != null ? Date.valueOf(deadline) : null);
            stmt.executeUpdate();

            // Create assignment entries for all enrolled students
            String enrollSql = "SELECT student_id FROM enrollments WHERE course_id = ?";
            try (PreparedStatement enrollStmt = connection.prepareStatement(enrollSql)) {
                enrollStmt.setInt(1, courseId);
                ResultSet rs = enrollStmt.executeQuery();

                String studentAssignSql = "INSERT INTO student_assignments (assignment_id, student_id) VALUES (?, ?)";
                try (PreparedStatement studentAssignStmt = connection.prepareStatement(studentAssignSql)) {
                    int assignmentId = getLastInsertId();
                    while (rs.next()) {
                        studentAssignStmt.setInt(1, assignmentId);
                        studentAssignStmt.setInt(2, rs.getInt("student_id"));
                        studentAssignStmt.addBatch();
                    }
                    studentAssignStmt.executeBatch();
                }
            }
        }
    }

    private int getLastInsertId() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT lastval()")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to get last insert ID");
    }

    private void notifyStudents(int courseId, String message) throws SQLException {
        String sql = "SELECT student_id FROM enrollments WHERE course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                addNotification(rs.getInt("student_id"), message, "Course Update");
            }
        }
    }

    private List<String> getInstructorCourses() {
        List<String> courses = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT title FROM courses WHERE instructor_id = ?")) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                courses.add(rs.getString("title"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching instructor courses: " + e.getMessage());
        }
        return courses;
    }

    private int getCourseIdByTitle(String title) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM courses WHERE title = ?")) {
            stmt.setString(1, title);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        throw new SQLException("Course not found: " + title);
    }

    private VBox createInstructorGradingTab() {
        VBox gradingPane = new VBox(10);
        gradingPane.setPadding(new Insets(20));
        gradingPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Grade Assignments and Quizzes");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        ComboBox<String> courseCombo = new ComboBox<>();
        courseCombo.getItems().addAll(getInstructorCourses());
        courseCombo.setPromptText("Select Course");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Assignments", "Quizzes");
        typeCombo.setPromptText("Select Type");

        TableView<Submission> submissionTable = new TableView<>();
        TableColumn<Submission, String> studentCol = new TableColumn<>("Student");
        studentCol.setCellValueFactory(cellData -> new SimpleStringProperty(getUsernameById(cellData.getValue().studentId)));
        TableColumn<Submission, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Submission, String> submissionCol = new TableColumn<>("Submission");
        submissionCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().submission));
        TableColumn<Submission, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().grade != null ? cellData.getValue().grade.toString() : "Ungraded"));
        TableColumn<Submission, String> feedbackCol = new TableColumn<>("Feedback");
        feedbackCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().feedback));
        submissionTable.getColumns().addAll(studentCol, titleCol, submissionCol, gradeCol, feedbackCol);

        TextField gradeField = new TextField();
        gradeField.setPromptText("Enter Grade (0-100)");
        TextArea feedbackField = new TextArea();
        feedbackField.setPromptText("Feedback");
        feedbackField.setPrefRowCount(3);

        Button submitGradeButton = new Button("Submit Grade");
        submitGradeButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");
        Button exportButton = new Button("Export Grades");
        exportButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        courseCombo.setOnAction(e -> updateSubmissionTable(submissionTable, courseCombo, typeCombo));
        typeCombo.setOnAction(e -> updateSubmissionTable(submissionTable, courseCombo, typeCombo));

        submitGradeButton.setOnAction(e -> {
            Submission selected = submissionTable.getSelectionModel().getSelectedItem();
            if (selected != null && courseCombo.getValue() != null && typeCombo.getValue() != null) {
                try {
                    int grade = Integer.parseInt(gradeField.getText());
                    if (grade < 0 || grade > 100) {
                        statusLabel.setText("Grade must be between 0 and 100.");
                        statusLabel.setTextFill(Color.RED);
                        return;
                    }
                    if ("Assignments".equals(typeCombo.getValue())) {
                        updateAssignmentGrade(selected.id, grade, feedbackField.getText());
                        updateStudentProgress(getCourseIdByTitle(courseCombo.getValue()), selected.studentId);
                    }
                    updateSubmissionTable(submissionTable, courseCombo, typeCombo);
                    statusLabel.setText("Grade submitted successfully!");
                    logActivity("Graded " + typeCombo.getValue() + ": " + selected.title + " for student: " + getUsernameById(selected.studentId));
                    addNotification(selected.studentId, "Your " + typeCombo.getValue().toLowerCase() + " '" + selected.title + "' was graded: " + grade, "Grade");
                    gradeField.clear();
                    feedbackField.clear();
                } catch (NumberFormatException ex) {
                    statusLabel.setText("Invalid grade format.");
                    statusLabel.setTextFill(Color.RED);
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        exportButton.setOnAction(e -> {
            try {
                exportGrades(submissionTable.getItems());
                statusLabel.setText("Grades exported successfully!");
                logActivity("Exported grades for course: " + courseCombo.getValue());
            } catch (IOException ex) {
                statusLabel.setText("Error exporting grades: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        GridPane formPane = new GridPane();
        formPane.setHgap(10);
        formPane.setVgap(10);
        formPane.setPadding(new Insets(10));
        formPane.add(new Label("Course:"), 0, 0);
        formPane.add(courseCombo, 1, 0);
        formPane.add(new Label("Type:"), 0, 1);
        formPane.add(typeCombo, 1, 1);
        formPane.add(new Label("Grade:"), 0, 2);
        formPane.add(gradeField, 1, 2);
        formPane.add(new Label("Feedback:"), 0, 3);
        formPane.add(feedbackField, 1, 3);

        HBox buttonPane = new HBox(10, submitGradeButton, exportButton);
        buttonPane.setAlignment(Pos.CENTER);

        gradingPane.getChildren().addAll(titleLabel, formPane, submissionTable, buttonPane, statusLabel);
        return gradingPane;
    }

    private void updateSubmissionTable(TableView<Submission> table, ComboBox<String> courseCombo, ComboBox<String> typeCombo) {
        if (courseCombo.getValue() == null || typeCombo.getValue() == null) return;
        try {
            int courseId = getCourseIdByTitle(courseCombo.getValue());
            List<Submission> submissions = "Assignments".equals(typeCombo.getValue())
                    ? getAssignmentSubmissions(courseId)
                    : getQuizSubmissions(courseId);
            table.setItems(FXCollections.observableArrayList(submissions));
        } catch (SQLException e) {
            showAlert("Error", "Failed to load submissions: " + e.getMessage());
        }
    }

    private List<Submission> getAssignmentSubmissions(int courseId) throws SQLException {
        List<Submission> submissions = new ArrayList<>();
        String sql = "SELECT sa.id, sa.student_id, a.title, sa.submission, sa.grade, sa.feedback " +
                "FROM student_assignments sa JOIN assignments a ON sa.assignment_id = a.id " +
                "WHERE a.course_id = ? AND sa.submission IS NOT NULL";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                submissions.add(new Submission(
                        rs.getInt("id"),
                        rs.getInt("student_id"),
                        rs.getString("title"),
                        rs.getString("submission"),
                        rs.getObject("grade") != null ? rs.getInt("grade") : null,
                        rs.getString("feedback")
                ));
            }
        }
        return submissions;
    }

    private List<Submission> getQuizSubmissions(int courseId) throws SQLException {
        List<Submission> submissions = new ArrayList<>();
        String sql = "SELECT qs.id, qs.student_id, q.title, qs.selected_option::text, " +
                "CASE WHEN qs.selected_option = q.correct_option THEN q.total_points ELSE 0 END as score " +
                "FROM quiz_submissions qs JOIN quizzes q ON qs.quiz_id = q.id WHERE q.course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                submissions.add(new Submission(
                        rs.getInt("id"),
                        rs.getInt("student_id"),
                        rs.getString("title"),
                        "Option " + rs.getString("selected_option"),
                        rs.getObject("score") != null ? rs.getInt("score") : null,
                        "Auto-graded"
                ));
            }
        }
        return submissions;
    }

    private void updateAssignmentGrade(int assignmentId, int grade, String feedback) throws SQLException {
        String sql = "UPDATE student_assignments SET grade = ?, feedback = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, grade);
            stmt.setString(2, feedback);
            stmt.setInt(3, assignmentId);
            stmt.executeUpdate();
        }
    }

    private void updateStudentProgress(int courseId, int studentId) throws SQLException {
        // Calculate average grade for assignments
        String assignSql = "SELECT AVG(COALESCE(sa.grade, 0)) as avg_grade " +
                "FROM student_assignments sa JOIN assignments a ON sa.assignment_id = a.id " +
                "WHERE a.course_id = ? AND sa.student_id = ? AND sa.grade IS NOT NULL";
        double assignAvg = 0;
        try (PreparedStatement stmt = connection.prepareStatement(assignSql)) {
            stmt.setInt(1, courseId);
            stmt.setInt(2, studentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) assignAvg = rs.getDouble("avg_grade");
        }

        // Calculate average grade for quizzes
        String quizSql = "SELECT AVG(COALESCE(CASE WHEN qs.selected_option = q.correct_option THEN q.total_points ELSE 0 END, 0)) as avg_grade " +
                "FROM quiz_submissions qs JOIN quizzes q ON qs.quiz_id = q.id " +
                "WHERE q.course_id = ? AND qs.student_id = ?";
        double quizAvg = 0;
        try (PreparedStatement stmt = connection.prepareStatement(quizSql)) {
            stmt.setInt(1, courseId);
            stmt.setInt(2, studentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) quizAvg = rs.getDouble("avg_grade");
        }

        // Calculate overall progress (average of assignments and quizzes)
        double overallProgress = (assignAvg + quizAvg) / 2;

        String updateSql = "UPDATE enrollments SET progress = ? WHERE course_id = ? AND student_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
            stmt.setInt(1, (int) overallProgress);
            stmt.setInt(2, courseId);
            stmt.setInt(3, studentId);
            stmt.executeUpdate();
        }
    }

    private void exportGrades(List<Submission> submissions) throws IOException {
        String fileName = "grades_export_" + LocalDate.now() + ".csv";
        try (PrintWriter writer = new PrintWriter(fileName)) {
            writer.println("Student,Title,Submission,Grade,Feedback");
            for (Submission sub : submissions) {
                String grade = sub.grade != null ? sub.grade.toString() : "Ungraded";
                String feedback = sub.feedback != null ? sub.feedback.replace(",", ";") : "";
                writer.println(String.format("%s,%s,%s,%s,%s",
                        getUsernameById(sub.studentId), sub.title, sub.submission, grade, feedback));
            }
        }
    }

    private VBox createInstructorCommTab() {
        VBox commPane = new VBox(10);
        commPane.setPadding(new Insets(20));
        commPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Communication");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        ComboBox<String> courseCombo = new ComboBox<>();
        courseCombo.getItems().addAll(getInstructorCourses());
        courseCombo.setPromptText("Select Course");

        ComboBox<String> studentCombo = new ComboBox<>();
        studentCombo.setPromptText("Select Student");

        TextArea messageField = new TextArea();
        messageField.setPromptText("Type your message here...");
        messageField.setPrefRowCount(5);

        Button sendButton = new Button("Send Message");
        sendButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        courseCombo.setOnAction(e -> {
            if (courseCombo.getValue() != null) {
                try {
                    studentCombo.getItems().setAll(getStudentsInCourse(getCourseIdByTitle(courseCombo.getValue())));
                } catch (SQLException ex) {
                    statusLabel.setText("Error loading students: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        sendButton.setOnAction(e -> {
            if (courseCombo.getValue() == null || studentCombo.getValue() == null || messageField.getText().isEmpty()) {
                statusLabel.setText("Course, student, and message are required.");
                statusLabel.setTextFill(Color.RED);
                return;
            }
            try {
                int courseId = getCourseIdByTitle(courseCombo.getValue());
                int receiverId = getUserIdByUsername(studentCombo.getValue());
                sendMessage(getUserIdByUsername(currentUser), receiverId, courseId, messageField.getText());
                statusLabel.setText("Message sent successfully!");
                logActivity("Sent message to student: " + studentCombo.getValue());
                addNotification(receiverId, "New message from instructor in course: " + courseCombo.getValue(), "Message");
                messageField.clear();
            } catch (SQLException ex) {
                statusLabel.setText("Error sending message: " + ex.getMessage());
                statusLabel.setTextFill(Color.RED);
            }
        });

        GridPane formPane = new GridPane();
        formPane.setHgap(10);
        formPane.setVgap(10);
        formPane.setPadding(new Insets(10));
        formPane.add(new Label("Course:"), 0, 0);
        formPane.add(courseCombo, 1, 0);
        formPane.add(new Label("Student:"), 0, 1);
        formPane.add(studentCombo, 1, 1);
        formPane.add(new Label("Message:"), 0, 2);
        formPane.add(messageField, 1, 2);

        commPane.getChildren().addAll(titleLabel, formPane, sendButton, statusLabel);
        return commPane;
    }

    private List<String> getStudentsInCourse(int courseId) throws SQLException {
        List<String> students = new ArrayList<>();
        String sql = "SELECT u.username FROM enrollments e JOIN users u ON e.student_id = u.id WHERE e.course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                students.add(rs.getString("username"));
            }
        }
        return students;
    }

    private void sendMessage(int senderId, int receiverId, int courseId, String content) throws SQLException {
        String sql = "INSERT INTO messages (sender_id, receiver_id, course_id, content, sent_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setInt(3, courseId);
            stmt.setString(4, content);
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }

    private void showStudentDashboard() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Tab overviewTab = new Tab("Overview");
        overviewTab.setClosable(false);
        overviewTab.setContent(createStudentOverviewTab());
        Tab coursesTab = new Tab("Courses");
        coursesTab.setClosable(false);
        coursesTab.setContent(createStudentCoursesTab());
        Tab notificationsTab = new Tab("Notifications");
        notificationsTab.setClosable(false);
        notificationsTab.setContent(createNotificationsTab());

        tabPane.getTabs().addAll(overviewTab, coursesTab, notificationsTab);
        rootLayout.setCenter(tabPane);
        logActivity("Accessed student dashboard");
    }

    private VBox createStudentOverviewTab() {
        VBox overviewPane = new VBox(10);
        overviewPane.setPadding(new Insets(20));
        overviewPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Student Overview");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<Enrollment> courseTable = new TableView<>();
        TableColumn<Enrollment, String> courseCol = new TableColumn<>("Course");
        courseCol.setCellValueFactory(cellData -> new SimpleStringProperty(getCourseTitleById(cellData.getValue().courseId)));
        TableColumn<Enrollment, String> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().progress + "%"));
        TableColumn<Enrollment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().progress >= passThreshold ? "Passing" : "Needs Improvement"));
        courseTable.getColumns().addAll(courseCol, progressCol, statusCol);
        courseTable.setItems(FXCollections.observableArrayList(getStudentEnrollments()));

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> progressChart = new BarChart<>(xAxis, yAxis);
        progressChart.setTitle("Course Progress");
        xAxis.setLabel("Course");
        yAxis.setLabel("Progress (%)");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Progress");
        for (Enrollment enrollment : getStudentEnrollments()) {
            series.getData().add(new XYChart.Data<>(getCourseTitleById(enrollment.courseId), enrollment.progress));
        }
        progressChart.getData().add(series);

        overviewPane.getChildren().addAll(titleLabel, courseTable, progressChart);
        return overviewPane;
    }

    private List<Enrollment> getStudentEnrollments() {
        List<Enrollment> enrollments = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT course_id, progress FROM enrollments WHERE student_id = ?")) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                enrollments.add(new Enrollment(rs.getInt("course_id"), rs.getInt("progress")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching enrollments: " + e.getMessage());
        }
        return enrollments;
    }

    private String getCourseTitleById(int courseId) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT title FROM courses WHERE id = ?")) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("title");
        } catch (SQLException e) {
            System.err.println("Error fetching course title: " + e.getMessage());
        }
        return "Unknown";
    }

    private VBox createStudentCoursesTab() {
        VBox coursesPane = new VBox(10);
        coursesPane.setPadding(new Insets(20));
        coursesPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("My Courses");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        ComboBox<String> courseCombo = new ComboBox<>();
        courseCombo.getItems().addAll(getStudentCourses());
        courseCombo.setPromptText("Select Course");

        TabPane contentTabs = new TabPane();
        Tab materialsTab = new Tab("Materials");
        materialsTab.setClosable(false);
        Tab assignmentsTab = new Tab("Assignments");
        assignmentsTab.setClosable(false);
        Tab quizzesTab = new Tab("Quizzes");
        quizzesTab.setClosable(false);
        contentTabs.getTabs().addAll(materialsTab, assignmentsTab, quizzesTab);

        courseCombo.setOnAction(e -> {
            if (courseCombo.getValue() != null) {
                try {
                    int courseId = getCourseIdByTitle(courseCombo.getValue());
                    materialsTab.setContent(createMaterialsView(courseId));
                    assignmentsTab.setContent(createAssignmentsView(courseId));
                    quizzesTab.setContent(createQuizzesView(courseId));
                } catch (SQLException ex) {
                    showAlert("Error", "Failed to load course content: " + ex.getMessage());
                }
            }
        });

        coursesPane.getChildren().addAll(titleLabel, courseCombo, contentTabs);
        return coursesPane;
    }

    private List<String> getStudentCourses() {
        List<String> courses = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT c.title FROM enrollments e JOIN courses c ON e.course_id = c.id WHERE e.student_id = ?")) {
            stmt.setInt(1, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                courses.add(rs.getString("title"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching student courses: " + e.getMessage());
        }
        return courses;
    }

    private VBox createMaterialsView(int courseId) {
        VBox materialsPane = new VBox(10);
        materialsPane.setPadding(new Insets(10));
        materialsPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        TableView<Material> materialsTable = new TableView<>();
        TableColumn<Material, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Material, String> dateCol = new TableColumn<>("Upload Date");
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().uploadDate.toString()));
        materialsTable.getColumns().addAll(titleCol, dateCol);

        TextArea contentArea = new TextArea();
        contentArea.setEditable(false);
        contentArea.setPrefHeight(200);

        Button downloadButton = new Button("Download Material");
        downloadButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "-fx-background-color: #42A5F5; -fx-text-fill: white;");

        try (PreparedStatement stmt = connection.prepareStatement("SELECT id, title, content, upload_date, file_path FROM materials WHERE course_id = ?")) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            List<Material> materials = new ArrayList<>();
            while (rs.next()) {
                materials.add(new Material(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getDate("upload_date").toLocalDate(),
                        rs.getString("file_path")
                ));
            }
            materialsTable.setItems(FXCollections.observableArrayList(materials));
        } catch (SQLException e) {
            showAlert("Error", "Failed to load materials: " + e.getMessage());
        }

        materialsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                contentArea.setText(newValue.content);
            }
        });

        downloadButton.setOnAction(e -> {
            Material selected = materialsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    downloadMaterial(selected);
                    showAlert("Success", "Material downloaded successfully!");
                    logActivity("Downloaded material: " + selected.title);
                } catch (IOException ex) {
                    showAlert("Error", "Failed to download material: " + ex.getMessage());
                }
            }
        });

        materialsPane.getChildren().addAll(materialsTable, contentArea, downloadButton);
        return materialsPane;
    }

    private void downloadMaterial(Material material) throws IOException {
        if (material.filePath != null && !material.filePath.isEmpty()) {
            // In a real application, you would implement file download logic here
            // For example, using Java's File and InputStream classes
            showAlert("Download", "Simulated download of: " + material.filePath);
        } else if (material.content != null && !material.content.isEmpty()) {
            // Save content as text file
            String fileName = material.title.replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt";
            try (PrintWriter writer = new PrintWriter(fileName)) {
                writer.println(material.content);
            }
        } else {
            throw new IOException("No content available for download");
        }
    }

    private VBox createAssignmentsView(int courseId) {
        VBox assignmentsPane = new VBox(10);
        assignmentsPane.setPadding(new Insets(10));
        assignmentsPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        TableView<Assignment> assignmentsTable = new TableView<>();
        TableColumn<Assignment, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Assignment, String> deadlineCol = new TableColumn<>("Deadline");
        deadlineCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().deadline.toString()));
        TableColumn<Assignment, String> submissionCol = new TableColumn<>("Submission");
        submissionCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().submission));
        TableColumn<Assignment, String> gradeCol = new TableColumn<>("Grade");
        gradeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().grade != null ? cellData.getValue().grade.toString() : "Ungraded"));
        TableColumn<Assignment, String> feedbackCol = new TableColumn<>("Feedback");
        feedbackCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().feedback));
        assignmentsTable.getColumns().addAll(titleCol, deadlineCol, submissionCol, gradeCol, feedbackCol);

        TextArea submissionArea = new TextArea();
        submissionArea.setPromptText("Enter your submission here...");
        submissionArea.setPrefRowCount(5);

        Button submitButton = new Button("Submit Assignment");
        submitButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        try {
            assignmentsTable.setItems(FXCollections.observableArrayList(getStudentAssignments(courseId)));
        } catch (SQLException e) {
            showAlert("Error", "Failed to load assignments: " + e.getMessage());
        }

        submitButton.setOnAction(e -> {
            Assignment selected = assignmentsTable.getSelectionModel().getSelectedItem();
            if (selected != null && !submissionArea.getText().isEmpty()) {
                try {
                    submitAssignment(selected.id, submissionArea.getText());
                    assignmentsTable.setItems(FXCollections.observableArrayList(getStudentAssignments(courseId)));
                    statusLabel.setText("Assignment submitted successfully!");
                    logActivity("Submitted assignment: " + selected.title);
                    addNotification(getUserIdByUsername(currentUser), "Assignment submitted: " + selected.title, "Submission");
                    submissionArea.clear();
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        assignmentsPane.getChildren().addAll(assignmentsTable, submissionArea, submitButton, statusLabel);
        return assignmentsPane;
    }

    private List<Assignment> getStudentAssignments(int courseId) throws SQLException {
        List<Assignment> assignments = new ArrayList<>();
        String sql = "SELECT sa.id, a.title, sa.submission, sa.grade, sa.feedback, a.deadline " +
                "FROM student_assignments sa JOIN assignments a ON sa.assignment_id = a.id " +
                "WHERE a.course_id = ? AND sa.student_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            stmt.setInt(2, getUserIdByUsername(currentUser));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                assignments.add(new Assignment(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("submission"),
                        rs.getObject("grade") != null ? rs.getInt("grade") : null,
                        rs.getString("feedback"),
                        rs.getDate("deadline").toLocalDate()
                ));
            }
        }
        return assignments;
    }

    private void submitAssignment(int assignmentId, String submission) throws SQLException {
        String sql = "UPDATE student_assignments SET submission = ?, submitted_date = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submission);
            stmt.setDate(2, Date.valueOf(LocalDate.now()));
            stmt.setInt(3, assignmentId);
            stmt.executeUpdate();
        }
    }

    private VBox createQuizzesView(int courseId) {
        VBox quizzesPane = new VBox(10);
        quizzesPane.setPadding(new Insets(10));
        quizzesPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        TableView<Quiz> quizzesTable = new TableView<>();
        TableColumn<Quiz, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        quizzesTable.getColumns().addAll(titleCol);

        VBox questionPane = new VBox(10);
        Label questionLabel = new Label();
        RadioButton option1 = new RadioButton();
        RadioButton option2 = new RadioButton();
        RadioButton option3 = new RadioButton();
        RadioButton option4 = new RadioButton();
        ToggleGroup toggleGroup = new ToggleGroup();
        option1.setToggleGroup(toggleGroup);
        option2.setToggleGroup(toggleGroup);
        option3.setToggleGroup(toggleGroup);
        option4.setToggleGroup(toggleGroup);

        Button submitButton = new Button("Submit Answer");
        submitButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        try {
            quizzesTable.setItems(FXCollections.observableArrayList(getQuizzes(courseId)));
        } catch (SQLException e) {
            showAlert("Error", "Failed to load quizzes: " + e.getMessage());
        }

        quizzesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                questionLabel.setText(newValue.question);
                option1.setText(newValue.options[0]);
                option2.setText(newValue.options[1]);
                option3.setText(newValue.options[2]);
                option4.setText(newValue.options[3]);
                toggleGroup.selectToggle(null);
            }
        });

        submitButton.setOnAction(e -> {
            Quiz selected = quizzesTable.getSelectionModel().getSelectedItem();
            RadioButton selectedOption = (RadioButton) toggleGroup.getSelectedToggle();
            if (selected != null && selectedOption != null) {
                try {
                    int selectedIndex = List.of(option1, option2, option3, option4).indexOf(selectedOption) + 1;
                    submitQuizAnswer(selected.id, selectedIndex);
                    statusLabel.setText("Answer submitted successfully!");
                    logActivity("Submitted quiz answer for: " + selected.title);
                    addNotification(getUserIdByUsername(currentUser), "Quiz answer submitted: " + selected.title, "Submission");
                    toggleGroup.selectToggle(null);

                    // Auto-grade the quiz
                    int score = (selectedIndex == selected.correctOption) ? selected.totalPoints : 0;
                    updateQuizGrade(selected.id, getUserIdByUsername(currentUser), score);
                    updateStudentProgress(courseId, getUserIdByUsername(currentUser));
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        questionPane.getChildren().addAll(questionLabel, option1, option2, option3, option4);
        quizzesPane.getChildren().addAll(quizzesTable, questionPane, submitButton, statusLabel);
        return quizzesPane;
    }

    private List<Quiz> getQuizzes(int courseId) throws SQLException {
        List<Quiz> quizzes = new ArrayList<>();
        String sql = "SELECT id, title, question, options, correct_option, total_points FROM quizzes WHERE course_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, courseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                quizzes.add(new Quiz(
                        rs.getInt("id"),
                        courseId,
                        rs.getString("title"),
                        rs.getString("question"),
                        (String[]) rs.getArray("options").getArray(),
                        rs.getInt("correct_option"),
                        rs.getInt("total_points")
                ));
            }
        }
        return quizzes;
    }

    private void submitQuizAnswer(int quizId, int selectedOption) throws SQLException {
        String sql = "INSERT INTO quiz_submissions (quiz_id, student_id, selected_option, submitted_date) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, quizId);
            stmt.setInt(2, getUserIdByUsername(currentUser));
            stmt.setInt(3, selectedOption);
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }

    private void updateQuizGrade(int quizId, int studentId, int score) throws SQLException {
        String sql = "UPDATE quiz_submissions SET score = ? WHERE quiz_id = ? AND student_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, score);
            stmt.setInt(2, quizId);
            stmt.setInt(3, studentId);
            stmt.executeUpdate();
        }
    }

    private void showCourses() {
        VBox coursesPane = new VBox(10);
        coursesPane.setPadding(new Insets(20));
        coursesPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Available Courses");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<Course> courseTable = new TableView<>();
        TableColumn<Course, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title));
        TableColumn<Course, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().description));
        TableColumn<Course, String> instructorCol = new TableColumn<>("Instructor");
        instructorCol.setCellValueFactory(cellData -> new SimpleStringProperty(getUsernameById(cellData.getValue().instructorId)));
        courseTable.getColumns().addAll(titleCol, descCol, instructorCol);
        courseTable.setItems(FXCollections.observableArrayList(getAllCourses()));

        Button enrollButton = new Button("Enroll");
        enrollButton.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "-fx-background-color: #66BB6A; -fx-text-fill: white;");

        Label statusLabel = new Label();
        statusLabel.setTextFill(Color.GREEN);

        enrollButton.setOnAction(e -> {
            Course selected = courseTable.getSelectionModel().getSelectedItem();
            if (selected != null && "Student".equals(currentRole)) {
                try {
                    enrollStudent(selected.id, getUserIdByUsername(currentUser));
                    statusLabel.setText("Enrolled successfully!");
                    logActivity("Enrolled in course: " + selected.title);
                    addNotification(getUserIdByUsername(currentUser), "Enrolled in course: " + selected.title, "Enrollment");
                } catch (SQLException ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setTextFill(Color.RED);
                }
            }
        });

        coursesPane.getChildren().addAll(titleLabel, courseTable, enrollButton, statusLabel);
        rootLayout.setCenter(coursesPane);
    }

    private void enrollStudent(int courseId, int studentId) throws SQLException {
        String sql = "INSERT INTO enrollments (student_id, course_id, enrolled_date) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, courseId);
            stmt.setDate(3, Date.valueOf(LocalDate.now()));
            stmt.executeUpdate();
        }
    }

    private void showStudents() {
        VBox studentsPane = new VBox(10);
        studentsPane.setPadding(new Insets(20));
        studentsPane.setStyle(currentTheme.equals("Light") ? "-fx-background-color: #f4f4f4;" : "-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Students");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        titleLabel.setTextFill(currentTheme.equals("Light") ? Color.DARKSLATEBLUE : Color.LIGHTBLUE);

        TableView<User> studentTable = new TableView<>();
        TableColumn<User, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().username));
        TableColumn<User, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email));
        studentTable.getColumns().addAll(usernameCol, emailCol);
        studentTable.setItems(FXCollections.observableArrayList(getAllUsers().stream()
                .filter(u -> "Student".equals(u.role))
                .collect(Collectors.toList())));

        studentsPane.getChildren().addAll(titleLabel, studentTable);
        rootLayout.setCenter(studentsPane);
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About LMS");
        alert.setHeaderText("Learning Management System");
        alert.setContentText("Version 1.0\nDeveloped by xAI\nA comprehensive platform for managing courses, assignments, and student progress.");
        alert.showAndWait();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Helper Classes
    private static class User {
        int id;
        String username;
        String email;
        String role;

        User(int id, String username, String email, String role) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
        }
    }

    private static class Course {
        int id;
        String title;
        String description;
        int instructorId;
        boolean approved;

        Course(int id, String title, String description, int instructorId, boolean approved) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.instructorId = instructorId;
            this.approved = approved;
        }
    }

    private static class Notification {
        int id;
        String content;
        String type;
        LocalDateTime createdAt;
        boolean isRead;

        Notification(int id, String content, String type, LocalDateTime createdAt, boolean isRead) {
            this.id = id;
            this.content = content;
            this.type = type;
            this.createdAt = createdAt;
            this.isRead = isRead;
        }
    }

    private static class Quiz {
        int id;
        int courseId;
        String title;
        String question;
        String[] options;
        int correctOption;
        int totalPoints;

        Quiz(int id, int courseId, String title, String question, String[] options, int correctOption) {
            this(id, courseId, title, question, options, correctOption, 100);
        }

        Quiz(int id, int courseId, String title, String question, String[] options, int correctOption, int totalPoints) {
            this.id = id;
            this.courseId = courseId;
            this.title = title;
            this.question = question;
            this.options = options;
            this.correctOption = correctOption;
            this.totalPoints = totalPoints;
        }
    }

    private static class Submission {
        int id;
        int studentId;
        String title;
        String submission;
        Integer grade;
        String feedback;

        Submission(int id, int studentId, String title, String submission, Integer grade, String feedback) {
            this.id = id;
            this.studentId = studentId;
            this.title = title;
            this.submission = submission;
            this.grade = grade;
            this.feedback = feedback;
        }
    }

    private static class Material {
        int id;
        String title;
        String content;
        LocalDate uploadDate;
        String filePath;

        Material(int id, String title, String content, LocalDate uploadDate) {
            this(id, title, content, uploadDate, null);
        }

        Material(int id, String title, String content, LocalDate uploadDate, String filePath) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.uploadDate = uploadDate;
            this.filePath = filePath;
        }
    }

    private static class Assignment {
        int id;
        String title;
        String submission;
        Integer grade;
        String feedback;
        LocalDate deadline;

        Assignment(int id, String title, String submission, Integer grade, String feedback, LocalDate deadline) {
            this.id = id;
            this.title = title;
            this.submission = submission;
            this.grade = grade;
            this.feedback = feedback;
            this.deadline = deadline;
        }
    }

    private static class Enrollment {
        int courseId;
        int progress;

        Enrollment(int courseId, int progress) {
            this.courseId = courseId;
            this.progress = progress;
        }
    }

    private static class HelpMessage {
        int id;
        int userId;
        String message;
        LocalDateTime createdAt;
        String status;

        HelpMessage(int id, int userId, String message, LocalDateTime createdAt, String status) {
            this.id = id;
            this.userId = userId;
            this.message = message;
            this.createdAt = createdAt;
            this.status = status;
        }
    }

    // Utility class for login dialog
    private static class Triple<T, U, V> {
        private final T first;
        private final U second;
        private final V third;

        Triple(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        T getFirst() { return first; }
        U getSecond() { return second; }
        V getThird() { return third; }
    }

    // Utility class for signup dialog
    private static class Quad<T, U, V, W> {
        private final T first;
        private final U second;
        private final V third;
        private final W fourth;

        Quad(T first, U second, V third, W fourth) {
            this.first = first;
            this.second = second;
            this.third = third;
            this.fourth = fourth;
        }

        T getFirst() { return first; }
        U getSecond() { return second; }
        V getThird() { return third; }
        W getFourth() { return fourth; }
    }
}