package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AircraftTest {

    private static final String ERROR_ENGINE_NOT_OPERATIONAL = "Cannot prepare for takeoff: engine is not operational.";
    private static final String ERROR_INSUFFICIENT_FUEL = "Cannot prepare for takeoff: insufficient fuel.";
    private static final String ERROR_ROUTE_UNAVAILABLE = "Cannot prepare for takeoff: route is unavailable.";
    private static final String ERROR_SENSOR_FAILURE = "Fuel sensor failure";
    private static final String ERROR_ENGINE_START_FAILURE = "The engine is not operational.";
    private static final String ERROR_TAKEOFF_FAILED = "Takeoff failed.";
    private static final String ERROR_CRUISE_FUEL_CAUSE = "Not enough fuel.";
    private static final String ERROR_CRUISE_CONSUMPTION = "A problem occurred while consuming fuel.";

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;

    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);

        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    private void stubAllPreFlightChecksPass() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    private void bringAircraftToInFlight() {
        stubAllPreFlightChecksPass();
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    @Test
    @DisplayName("prepareForTakeoff checks engine, fuel and route once each, then becomes READY")
    void prepareForTakeoffSucceedsWhenAllChecksPass() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());

        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();

        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    @Test
    @DisplayName("prepareForTakeoff fails fast when the engine reports it is not operational")
    void prepareForTakeoffFailsWhenEngineIsNotOperational() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception =
                assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(ERROR_ENGINE_NOT_OPERATIONAL, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        verify(engine, times(1)).isOperational();
        verify(fuelSystem, never()).hasEnoughFuel();
        verify(navigationSystem, never()).isRouteAvailable();
    }

    @Test
    @DisplayName("prepareForTakeoff fails when there is not enough fuel or no route")
    void prepareForTakeoffFailsWhenFuelOrRouteIsMissing() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);

        FlightException fuelException =
                assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals(ERROR_INSUFFICIENT_FUEL, fuelException.getMessage());
        verify(navigationSystem, never()).isRouteAvailable();

        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException routeException =
                assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals(ERROR_ROUTE_UNAVAILABLE, routeException.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    @Test
    @DisplayName("an exception thrown by a component check propagates and the aircraft stays GROUNDED")
    void prepareForTakeoffPropagatesSensorFailure() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenThrow(new IllegalStateException(ERROR_SENSOR_FAILURE));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(ERROR_SENSOR_FAILURE, exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, never()).isRouteAvailable();
    }

    @Test
    @DisplayName("takeOff starts the engine and then retracts the landing gear")
    void takeOffStartsEngineThenRetractsLandingGear() {
        stubAllPreFlightChecksPass();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        InOrder order = inOrder(engine, landingGear);
        order.verify(engine, times(1)).start();
        order.verify(landingGear, times(1)).retract();
    }

    @Test
    @DisplayName("takeOff is aborted when the engine fails to start")
    void takeOffAbortsWhenEngineFailsToStart() {
        stubAllPreFlightChecksPass();
        IllegalStateException startFailure = new IllegalStateException(ERROR_ENGINE_START_FAILURE);
        doThrow(startFailure).when(engine).start();

        aircraft.prepareForTakeoff();
        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals(ERROR_TAKEOFF_FAILED, exception.getMessage());
        assertSame(startFailure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        verify(landingGear, never()).retract();
    }

    @Test
    @DisplayName("each cruise call consumes fuel through the fuel system")
    void cruiseConsumesFuelOnEveryCall() {
        bringAircraftToInFlight();
        doNothing().when(fuelSystem).consumeFuel(anyDouble());

        aircraft.cruise(100.0);
        aircraft.cruise(250.5);
        aircraft.cruise(42.0);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        verify(fuelSystem, times(3)).consumeFuel(any(Double.class));
        verify(fuelSystem, times(1)).consumeFuel(250.5);
        verify(landingGear, never()).deploy();
        verify(engine, never()).stop();
    }

    @Test
    @DisplayName("cruise rejects a non-positive fuel amount without touching the fuel system")
    void cruiseRejectsNonPositiveFuelAmount() {
        bringAircraftToInFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(0));
        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(-10));

        verify(fuelSystem, never()).consumeFuel(anyDouble());
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    @Test
    @DisplayName("engine failure during cruise causes an emergency landing without consuming fuel")
    void cruiseWithFailedEngineTriggersEmergencyLanding() {
        bringAircraftToInFlight();

        reset(engine, fuelSystem, landingGear);

        aircraft.cruise(100.0);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, never()).consumeFuel(anyDouble());

        InOrder order = inOrder(landingGear, engine);
        order.verify(landingGear, times(1)).deploy();
        order.verify(engine, times(1)).stop();
        verifyNoMoreInteractions(engine, landingGear);
    }

    @Test
    @DisplayName("a fuel consumption failure triggers an emergency landing and a FlightException")
    void cruiseFuelFailureTriggersEmergencyLanding() {
        bringAircraftToInFlight();
        IllegalStateException fuelFailure = new IllegalStateException(ERROR_CRUISE_FUEL_CAUSE);
        doThrow(fuelFailure).when(fuelSystem).consumeFuel(anyDouble());

        FlightException exception =
                assertThrows(FlightException.class, () -> aircraft.cruise(5_000.0));

        assertEquals(ERROR_CRUISE_CONSUMPTION, exception.getMessage());
        assertSame(fuelFailure, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(5_000.0);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("land deploys the landing gear, stops the engine and the aircraft can fly again")
    void landDeploysGearStopsEngineAndAllowsAnotherFlight() {
        bringAircraftToInFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        InOrder order = inOrder(landingGear, engine);
        order.verify(landingGear).deploy();
        order.verify(engine).stop();

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, times(2)).isOperational();
    }

    @Test
    @DisplayName("invalid lifecycle operations throw FlightException and touch no component")
    void invalidLifecycleOperationsAreRejected() {
        assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertThrows(FlightException.class, () -> aircraft.cruise(10));
        assertThrows(FlightException.class, () -> aircraft.land());
        assertThrows(FlightException.class, () -> aircraft.emergencyLand());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, fuelSystem, landingGear, navigationSystem);
    }
}