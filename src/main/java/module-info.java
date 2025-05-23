module com.example.lms {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.management;

    opens com.example.lms to javafx.fxml;
    exports com.example.lms;
}