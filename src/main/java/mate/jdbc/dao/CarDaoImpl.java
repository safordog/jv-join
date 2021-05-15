package mate.jdbc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import mate.jdbc.lib.Dao;
import mate.jdbc.lib.exception.DataProcessingException;
import mate.jdbc.model.Car;
import mate.jdbc.model.Driver;
import mate.jdbc.model.Manufacturer;
import mate.jdbc.util.ConnectionUtil;

@Dao
public class CarDaoImpl implements CarDao {
    @Override
    public Car create(Car car) {
        String query = "INSERT INTO cars (model, manufacturer_id) VALUES (?, ?)";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement insertStatement =
                        connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            insertStatement.setString(1, car.getModel());
            insertStatement.setLong(2, car.getManufacturer().getId());
            insertStatement.executeUpdate();
            ResultSet resultSet = insertStatement.getGeneratedKeys();
            if (resultSet.next()) {
                car.setId(resultSet.getObject(1, Long.class));
                saveDrivers(car);
            }
            return car;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't create car: "
                    + car + ". ", throwable);
        }
    }

    @Override
    public Car get(Long id) {
        String query = "SELECT c.id, model, manufacturer_id, name, country "
                + "FROM cars c JOIN manufacturers m ON c.manufacturer_id = m.id "
                + "WHERE c.id = ? AND c.is_deleted = FALSE";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement getByIdStatement = connection.prepareStatement(query)) {
            getByIdStatement.setLong(1, id);
            ResultSet resultSet = getByIdStatement.executeQuery();
            Car car = null;
            if (resultSet.next()) {
                car = getCarFromResultSet(resultSet);
                car.setDrivers(getDriversForCar(id));
            }
            return car;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't get driver by id " + id, throwable);
        }
    }

    @Override
    public List<Car> getAll() {
        String query = "SELECT c.id, model, manufacturer_id, name, country "
                + "FROM cars c JOIN manufacturers m on c.manufacturer_id = m.id "
                + "WHERE c.is_deleted = FALSE";
        try (Connection connection = ConnectionUtil.getConnection();
                Statement getAllStatement = connection.createStatement()) {
            ResultSet resultSet = getAllStatement.executeQuery(query);
            List<Car> cars = new ArrayList<>();
            Car car;
            while (resultSet.next()) {
                car = getCarFromResultSet(resultSet);
                car.setDrivers(getDriversForCar(car.getId()));
                cars.add(car);
            }
            return cars;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't get a list of cars from DB.",
                    throwable);
        }
    }

    @Override
    public Car update(Car car) {
        String query = "UPDATE cars SET model = ?, manufacturer_id = ?"
                + " WHERE id = ? AND is_deleted = FALSE";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement updateStatement = connection.prepareStatement(query)) {
            updateStatement.setString(1, car.getModel());
            updateStatement.setLong(2, car.getManufacturer().getId());
            updateStatement.setLong(3, car.getId());
            updateStatement.executeUpdate();
            deleteCarDrivers(car.getId());
            saveDrivers(car);
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't update "
                    + car + " in DB.", throwable);
        }
        return car;
    }

    @Override
    public boolean delete(Long id) {
        String query = "UPDATE cars SET is_deleted = TRUE WHERE id = ?";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement deleteStatement = connection.prepareStatement(query)) {
            deleteStatement.setLong(1, id);
            return deleteStatement.executeUpdate() > 0;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't delete car with id " + id, throwable);
        }
    }

    @Override
    public List<Car> getAllByDriver(Long driverId) {
        String query = "SELECT car_id FROM cars_drivers c_d JOIN cars c"
                + " ON c.id = c_d.car_id"
                + " WHERE driver_id = ? AND c.is_deleted = FALSE";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement getAllStatement = connection.prepareStatement(query)) {
            getAllStatement.setLong(1, driverId);
            ResultSet resultSet = getAllStatement.executeQuery();
            List<Car> cars = new ArrayList<>();
            while (resultSet.next()) {
                cars.add(get(resultSet.getObject(1, Long.class)));
            }
            return cars;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't get all cars for driver with id: "
                    + driverId, throwable);
        }
    }

    private Car getCarFromResultSet(ResultSet resultSet) throws SQLException {
        Long manufacturerId = resultSet.getObject("manufacturer_id", Long.class);
        String manufacturerName = resultSet.getString("name");
        String manufacturerCountry = resultSet.getString("country");
        Manufacturer manufacturer = new Manufacturer(manufacturerName, manufacturerCountry);
        manufacturer.setId(manufacturerId);
        Long carId = resultSet.getObject(1, Long.class);
        String carModel = resultSet.getString("model");
        Car car = new Car(carModel, manufacturer);
        car.setId(carId);
        return car;
    }

    private List<Driver> getDriversForCar(Long carId) {
        String query = "SELECT id, name, license_number FROM drivers d "
                + "JOIN cars_drivers c_d ON d.id = c_d.driver_id "
                + "WHERE car_id = ? AND d.is_deleted = FALSE";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement getByIdStatement = connection.prepareStatement(query)) {
            getByIdStatement.setLong(1, carId);
            ResultSet resultSet = getByIdStatement.executeQuery();
            List<Driver> drivers = new ArrayList<>();
            while (resultSet.next()) {
                drivers.add(getDriverFromResultSet(resultSet));
            }
            return drivers;
        } catch (SQLException throwable) {
            throw new DataProcessingException("Can't get car from DB with id: " + carId, throwable);
        }
    }

    private Driver getDriverFromResultSet(ResultSet resultSet) throws SQLException {
        Long id = resultSet.getObject("id", Long.class);
        String name = resultSet.getString("name");
        String licenseNumber = resultSet.getString("license_number");
        return new Driver(id, name, licenseNumber);
    }

    private void saveDrivers(Car car) throws SQLException {
        String query = "INSERT cars_drivers (car_id, driver_id) VALUES (?, ?)";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement insertStatement = connection.prepareStatement(query)) {
            insertStatement.setLong(1, car.getId());
            for (Driver driver : car.getDrivers()) {
                insertStatement.setLong(2, driver.getId());
                insertStatement.executeUpdate();
            }
        }
    }

    private void deleteCarDrivers(Long id) throws SQLException {
        String query = "DELETE FROM cars_drivers WHERE car_id = ?";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement deleteStatement = connection.prepareStatement(query)) {
            deleteStatement.setLong(1, id);
            deleteStatement.executeUpdate();
        }
    }
}
