import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import java.time.*;
import java.time.format.*;

public class ELearningServer {
    private static final int PORT = 8080;
    private static Connection dbConnection;
    private static ExecutorService threadPool = Executors.newFixedThreadPool(10);
    
    public static void main(String[] args) throws Exception {
        initDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", new FileHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/courses", new CoursesHandler());
        server.createContext("/api/enroll", new EnrollHandler());
        server.createContext("/api/progress", new ProgressHandler());
        server.createContext("/api/quiz", new QuizHandler());
        server.createContext("/api/attendance", new AttendanceHandler());
        server.createContext("/api/mark-attendance", new MarkAttendanceHandler());
        server.createContext("/api/timetable", new TimetableHandler());
        server.createContext("/api/add-course", new AddCourseHandler());
        server.createContext("/api/delete-course", new DeleteCourseHandler());
        server.createContext("/api/add-timetable", new AddTimetableHandler());
        
        server.setExecutor(threadPool);
        server.start();
        System.out.println("Server started on port " + PORT);
    }
    
    private static void initDatabase() throws SQLException {
        dbConnection = DriverManager.getConnection("jdbc:sqlite:elearning.db");
        Statement stmt = dbConnection.createStatement();
        
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password TEXT, role TEXT, email TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS courses (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT, instructor TEXT, duration TEXT, credits INTEGER, category TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS enrollments (id INTEGER PRIMARY KEY, user_id INTEGER, course_id INTEGER, progress INTEGER DEFAULT 0, enrollment_date TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS quizzes (id INTEGER PRIMARY KEY, course_id INTEGER, question TEXT, options TEXT, answer INTEGER)");
        stmt.execute("CREATE TABLE IF NOT EXISTS attendance (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, course_id INTEGER, date TEXT, status TEXT, marked_at TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS timetable (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER, day TEXT, start_time TEXT, end_time TEXT, room TEXT, instructor TEXT)");
        
        // Insert sample data if empty
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM courses");
        if (rs.next() && rs.getInt(1) == 0) {
            stmt.execute("INSERT INTO courses VALUES (1, 'Java Programming', 'Learn Java from basics to advanced', 'Dr. Smith', '8 weeks', 4, 'Programming')");
            stmt.execute("INSERT INTO courses VALUES (2, 'Web Development', 'HTML, CSS, JavaScript fundamentals', 'Prof. Johnson', '6 weeks', 3, 'Web')");
            stmt.execute("INSERT INTO courses VALUES (3, 'Database Systems', 'SQL and database design principles', 'Dr. Williams', '10 weeks', 4, 'Database')");
            stmt.execute("INSERT INTO courses VALUES (4, 'Data Structures', 'Learn algorithms and data structures', 'Dr. Anderson', '12 weeks', 5, 'Programming')");
            stmt.execute("INSERT INTO courses VALUES (5, 'Machine Learning', 'Introduction to ML and AI concepts', 'Prof. Martinez', '10 weeks', 4, 'AI')");
            
            stmt.execute("INSERT INTO quizzes VALUES (1, 1, 'What is Java?', 'Programming Language|Database|Operating System|Web Browser', 0)");
            stmt.execute("INSERT INTO quizzes VALUES (2, 1, 'Java is platform independent?', 'True|False', 0)");
            stmt.execute("INSERT INTO quizzes VALUES (3, 2, 'What does HTML stand for?', 'Hyper Text Markup Language|High Tech Modern Language|Home Tool Markup Language|Hyperlinks and Text Markup Language', 0)");
            
            // Sample timetable
            stmt.execute("INSERT INTO timetable VALUES (1, 1, 'Monday', '09:00', '11:00', 'Room 101', 'Dr. Smith')");
            stmt.execute("INSERT INTO timetable VALUES (2, 1, 'Wednesday', '09:00', '11:00', 'Room 101', 'Dr. Smith')");
            stmt.execute("INSERT INTO timetable VALUES (3, 2, 'Tuesday', '14:00', '16:00', 'Lab 201', 'Prof. Johnson')");
            stmt.execute("INSERT INTO timetable VALUES (4, 2, 'Thursday', '14:00', '16:00', 'Lab 201', 'Prof. Johnson')");
            stmt.execute("INSERT INTO timetable VALUES (5, 3, 'Monday', '11:00', '13:00', 'Room 102', 'Dr. Williams')");
            stmt.execute("INSERT INTO timetable VALUES (6, 3, 'Friday', '11:00', '13:00', 'Room 102', 'Dr. Williams')");
            stmt.execute("INSERT INTO timetable VALUES (7, 4, 'Tuesday', '09:00', '11:00', 'Room 103', 'Dr. Anderson')");
            stmt.execute("INSERT INTO timetable VALUES (8, 5, 'Wednesday', '14:00', '16:00', 'Lab 202', 'Prof. Martinez')");
        }
    }
    
    static class FileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String response = "<!DOCTYPE html><html><head><title>Redirect</title></head><body><script>window.location.href='index.html';</script></body></html>";
            ex.getResponseHeaders().set("Content-Type", "text/html");
            ex.sendResponseHeaders(200, response.length());
            ex.getResponseBody().write(response.getBytes());
            ex.close();
        }
    }
    
    static class RegisterHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            setCORS(ex);
            
            // Handle OPTIONS preflight request
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            
            threadPool.submit(() -> {
                try {
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        // Validate input
                        if (json.getString("username").trim().isEmpty() || 
                            json.getString("email").trim().isEmpty() ||
                            json.getString("password").trim().isEmpty()) {
                            sendJSON(ex, new JSONObject().put("success", false).put("message", "All fields are required"));
                            return;
                        }
                        
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "INSERT INTO users (username, password, role, email) VALUES (?, ?, ?, ?)");
                        stmt.setString(1, json.getString("username").trim());
                        stmt.setString(2, json.getString("password"));
                        stmt.setString(3, json.optString("role", "student"));
                        stmt.setString(4, json.getString("email").trim());
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Registration successful! You can now login."));
                    }
                } catch (SQLException e) {
                    try {
                        if (e.getMessage().contains("UNIQUE")) {
                            sendJSON(ex, new JSONObject().put("success", false).put("message", "Username already exists"));
                        } else {
                            sendJSON(ex, new JSONObject().put("success", false).put("message", "Registration failed: " + e.getMessage()));
                        }
                    } catch (IOException ignored) {}
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", "Error: " + e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            setCORS(ex);
            
            // Handle OPTIONS preflight request
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            
            threadPool.submit(() -> {
                try {
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "SELECT id, username, role FROM users WHERE username=? AND password=?");
                        stmt.setString(1, json.getString("username"));
                        stmt.setString(2, json.getString("password"));
                        ResultSet rs = stmt.executeQuery();
                        
                        if (rs.next()) {
                            JSONObject user = new JSONObject();
                            user.put("id", rs.getInt("id"));
                            user.put("username", rs.getString("username"));
                            user.put("role", rs.getString("role"));
                            sendJSON(ex, new JSONObject().put("success", true).put("user", user));
                        } else {
                            sendJSON(ex, new JSONObject().put("success", false).put("message", "Invalid username or password"));
                        }
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", "Login error: " + e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class CoursesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    Statement stmt = dbConnection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM courses");
                    
                    JSONArray courses = new JSONArray();
                    while (rs.next()) {
                        JSONObject course = new JSONObject();
                        course.put("id", rs.getInt("id"));
                        course.put("title", rs.getString("title"));
                        course.put("description", rs.getString("description"));
                        course.put("instructor", rs.getString("instructor"));
                        course.put("duration", rs.getString("duration"));
                        course.put("credits", rs.getInt("credits"));
                        course.put("category", rs.getString("category"));
                        courses.put(course);
                    }
                    
                    sendJSON(ex, new JSONObject().put("courses", courses));
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("error", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class EnrollHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        String currentDate = LocalDate.now().toString();
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "INSERT INTO enrollments (user_id, course_id, progress, enrollment_date) VALUES (?, ?, 0, ?)");
                        stmt.setInt(1, json.getInt("userId"));
                        stmt.setInt(2, json.getInt("courseId"));
                        stmt.setString(3, currentDate);
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Enrolled successfully"));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class ProgressHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    String query = ex.getRequestURI().getQuery();
                    int userId = Integer.parseInt(query.split("=")[1]);
                    
                    PreparedStatement stmt = dbConnection.prepareStatement(
                        "SELECT c.*, e.progress, e.enrollment_date FROM enrollments e JOIN courses c ON e.course_id=c.id WHERE e.user_id=?");
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    
                    JSONArray enrolled = new JSONArray();
                    while (rs.next()) {
                        JSONObject item = new JSONObject();
                        item.put("id", rs.getInt("id"));
                        item.put("title", rs.getString("title"));
                        item.put("instructor", rs.getString("instructor"));
                        item.put("credits", rs.getInt("credits"));
                        item.put("progress", rs.getInt("progress"));
                        item.put("enrollmentDate", rs.getString("enrollment_date"));
                        enrolled.put(item);
                    }
                    
                    sendJSON(ex, new JSONObject().put("enrolled", enrolled));
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("error", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class QuizHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    String query = ex.getRequestURI().getQuery();
                    int courseId = Integer.parseInt(query.split("=")[1]);
                    
                    PreparedStatement stmt = dbConnection.prepareStatement(
                        "SELECT * FROM quizzes WHERE course_id=?");
                    stmt.setInt(1, courseId);
                    ResultSet rs = stmt.executeQuery();
                    
                    JSONArray quizzes = new JSONArray();
                    while (rs.next()) {
                        JSONObject quiz = new JSONObject();
                        quiz.put("id", rs.getInt("id"));
                        quiz.put("question", rs.getString("question"));
                        quiz.put("options", new JSONArray(rs.getString("options").split("\\|")));
                        quizzes.put(quiz);
                    }
                    
                    sendJSON(ex, new JSONObject().put("quizzes", quizzes));
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("error", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class AttendanceHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    String query = ex.getRequestURI().getQuery();
                    int userId = Integer.parseInt(query.split("=")[1]);
                    
                    PreparedStatement stmt = dbConnection.prepareStatement(
                        "SELECT a.*, c.title FROM attendance a JOIN courses c ON a.course_id=c.id WHERE a.user_id=? ORDER BY a.date DESC");
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    
                    JSONArray records = new JSONArray();
                    while (rs.next()) {
                        JSONObject record = new JSONObject();
                        record.put("courseTitle", rs.getString("title"));
                        record.put("date", rs.getString("date"));
                        record.put("status", rs.getString("status"));
                        records.put(record);
                    }
                    
                    sendJSON(ex, new JSONObject().put("attendance", records));
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("error", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class MarkAttendanceHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        String currentDate = LocalDate.now().toString();
                        String currentTime = LocalDateTime.now().toString();
                        
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "INSERT INTO attendance (user_id, course_id, date, status, marked_at) VALUES (?, ?, ?, ?, ?)");
                        stmt.setInt(1, json.getInt("userId"));
                        stmt.setInt(2, json.getInt("courseId"));
                        stmt.setString(3, currentDate);
                        stmt.setString(4, json.getString("status"));
                        stmt.setString(5, currentTime);
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Attendance marked"));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class TimetableHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    String query = ex.getRequestURI().getQuery();
                    
                    if (query != null && query.contains("userId")) {
                        int userId = Integer.parseInt(query.split("=")[1]);
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "SELECT t.*, c.title FROM timetable t JOIN courses c ON t.course_id=c.id JOIN enrollments e ON c.id=e.course_id WHERE e.user_id=? ORDER BY CASE t.day WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6 WHEN 'Sunday' THEN 7 END, t.start_time");
                        stmt.setInt(1, userId);
                        ResultSet rs = stmt.executeQuery();
                        
                        JSONArray schedule = new JSONArray();
                        while (rs.next()) {
                            JSONObject item = new JSONObject();
                            item.put("courseTitle", rs.getString("title"));
                            item.put("day", rs.getString("day"));
                            item.put("startTime", rs.getString("start_time"));
                            item.put("endTime", rs.getString("end_time"));
                            item.put("room", rs.getString("room"));
                            item.put("instructor", rs.getString("instructor"));
                            schedule.put(item);
                        }
                        
                        sendJSON(ex, new JSONObject().put("timetable", schedule));
                    } else {
                        Statement stmt = dbConnection.createStatement();
                        ResultSet rs = stmt.executeQuery(
                            "SELECT t.*, c.title FROM timetable t JOIN courses c ON t.course_id=c.id ORDER BY CASE t.day WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6 WHEN 'Sunday' THEN 7 END, t.start_time");
                        
                        JSONArray schedule = new JSONArray();
                        while (rs.next()) {
                            JSONObject item = new JSONObject();
                            item.put("id", rs.getInt("id"));
                            item.put("courseId", rs.getInt("course_id"));
                            item.put("courseTitle", rs.getString("title"));
                            item.put("day", rs.getString("day"));
                            item.put("startTime", rs.getString("start_time"));
                            item.put("endTime", rs.getString("end_time"));
                            item.put("room", rs.getString("room"));
                            item.put("instructor", rs.getString("instructor"));
                            schedule.put(item);
                        }
                        
                        sendJSON(ex, new JSONObject().put("timetable", schedule));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("error", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class AddCourseHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "INSERT INTO courses (title, description, instructor, duration, credits, category) VALUES (?, ?, ?, ?, ?, ?)");
                        stmt.setString(1, json.getString("title"));
                        stmt.setString(2, json.getString("description"));
                        stmt.setString(3, json.getString("instructor"));
                        stmt.setString(4, json.getString("duration"));
                        stmt.setInt(5, json.getInt("credits"));
                        stmt.setString(6, json.getString("category"));
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Course added successfully"));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class DeleteCourseHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        PreparedStatement stmt = dbConnection.prepareStatement("DELETE FROM courses WHERE id=?");
                        stmt.setInt(1, json.getInt("courseId"));
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Course deleted"));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    static class AddTimetableHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            threadPool.submit(() -> {
                try {
                    setCORS(ex);
                    if ("POST".equals(ex.getRequestMethod())) {
                        String body = new String(ex.getRequestBody().readAllBytes());
                        JSONObject json = new JSONObject(body);
                        
                        PreparedStatement stmt = dbConnection.prepareStatement(
                            "INSERT INTO timetable (course_id, day, start_time, end_time, room, instructor) VALUES (?, ?, ?, ?, ?, ?)");
                        stmt.setInt(1, json.getInt("courseId"));
                        stmt.setString(2, json.getString("day"));
                        stmt.setString(3, json.getString("startTime"));
                        stmt.setString(4, json.getString("endTime"));
                        stmt.setString(5, json.getString("room"));
                        stmt.setString(6, json.getString("instructor"));
                        stmt.executeUpdate();
                        
                        sendJSON(ex, new JSONObject().put("success", true).put("message", "Timetable entry added"));
                    }
                } catch (Exception e) {
                    try {
                        sendJSON(ex, new JSONObject().put("success", false).put("message", e.getMessage()));
                    } catch (IOException ignored) {}
                }
            });
        }
    }
    
    private static void setCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
    
    private static void sendJSON(HttpExchange ex, JSONObject json) throws IOException {
        String response = json.toString();
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, response.length());
        ex.getResponseBody().write(response.getBytes());
        ex.close();
    }
}