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

/**
 * Tests for {@link Aircraft} that replace every component with a Mockito test double.
 *
 * <ul>
 *   <li><b>Stub</b>: we configure a double's answers ({@code when(...).thenReturn(...)},
 *       {@code thenThrow(...)}, {@code doNothing()}, {@code doThrow()}) to put the
 *       aircraft into the situation we want to test.</li>
 *   <li><b>Mock</b>: after exercising the aircraft we ask the double what happened
 *       to it ({@code verify(...)}, {@code times(...)}, {@code never()}).</li>
 * </ul>
 */
class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;

    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        // mock(): create doubles that have the same type as the real components.
        // Unstubbed boolean methods return false and void methods do nothing.
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);

        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    /** Stubs all pre-flight checks to pass. */
    private void stubAllPreFlightChecksPass() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    /** Drives the aircraft from GROUNDED to IN_FLIGHT. */
    private void bringAircraftToInFlight() {
        stubAllPreFlightChecksPass();
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    // ---------------------------------------------------------------------
    // 1. mock(), when(), thenReturn(), verify(), times()
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("prepareForTakeoff checks engine, fuel and route once each, then becomes READY")
    void prepareForTakeoffSucceedsWhenAllChecksPass() {
        // Stub: every component reports that it is ready.
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());

        // Mock: each check was asked exactly once.
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();

        // Preparation must not start the engine or move the landing gear.
        verify(engine, never()).start();
        verifyNoInteractions(landingGear);
    }

    // ---------------------------------------------------------------------
    // 2. when()/thenReturn() as a stub to simulate failing pre-flight checks
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("prepareForTakeoff fails fast when the engine reports it is not operational")
    void prepareForTakeoffFailsWhenEngineIsNotOperational() {
        // Stub: the engine says "I'm broken".
        when(engine.isOperational()).thenReturn(false);

        FlightException exception =
                assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals("Cannot prepare for takeoff: engine is not operational.",
                exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // The remaining checks are skipped because the engine check failed first.
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
        assertEquals("Cannot prepare for takeoff: insufficient fuel.", fuelException.getMessage());
        verify(navigationSystem, never()).isRouteAvailable();

        // Re-stub: now fuel is fine but the route is unavailable.
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);

        FlightException routeException =
                assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals("Cannot prepare for takeoff: route is unavailable.",
                routeException.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    // ---------------------------------------------------------------------
    // 3. thenThrow(): a non-void method throws while it is being queried
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("an exception thrown by a component check propagates and the aircraft stays GROUNDED")
    void prepareForTakeoffPropagatesSensorFailure() {
        when(engine.isOperational()).thenReturn(true);
        // Stub: the fuel sensor itself breaks when it is read.
        when(fuelSystem.hasEnoughFuel()).thenThrow(new IllegalStateException("Fuel sensor failure"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> aircraft.prepareForTakeoff());

        assertEquals("Fuel sensor failure", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, never()).isRouteAvailable();
    }

    // ---------------------------------------------------------------------
    // 4. doNothing(), verify() with InOrder
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("takeOff starts the engine and then retracts the landing gear")
    void takeOffStartsEngineThenRetractsLandingGear() {
        stubAllPreFlightChecksPass();
        // Stub void methods explicitly: starting the engine and retracting the gear succeed.
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Mock: both actions happened exactly once and in the correct order.
        InOrder order = inOrder(engine, landingGear);
        order.verify(engine, times(1)).start();
        order.verify(landingGear, times(1)).retract();
    }

    @Test
    @DisplayName("takeOff is aborted when the engine fails to start")
    void takeOffAbortsWhenEngineFailsToStart() {
        stubAllPreFlightChecksPass();
        // Stub: start() is void, so doThrow() is used instead of when().thenThrow().
        IllegalStateException startFailure = new IllegalStateException("The engine is not operational.");
        doThrow(startFailure).when(engine).start();

        aircraft.prepareForTakeoff();
        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals("Takeoff failed.", exception.getMessage());
        assertSame(startFailure, exception.getCause());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        // The gear must stay down if the engine never started.
        verify(landingGear, never()).retract();
    }

    // ---------------------------------------------------------------------
    // 5. any() / anyDouble(), times() with more than one call
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("each cruise call consumes fuel through the fuel system")
    void cruiseConsumesFuelOnEveryCall() {
        bringAircraftToInFlight();
        doNothing().when(fuelSystem).consumeFuel(anyDouble());

        aircraft.cruise(100.0);
        aircraft.cruise(250.5);
        aircraft.cruise(42.0);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // any(): we do not care about the exact amounts here, only that fuel was consumed 3 times.
        verify(fuelSystem, times(3)).consumeFuel(any(Double.class));
        // Exact-value verification still works alongside the matcher.
        verify(fuelSystem, times(1)).consumeFuel(250.5);
        // No landing happened during a normal cruise.
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

    // ---------------------------------------------------------------------
    // 6. reset(): forget the setup-phase stubs and interactions mid-test
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("engine failure during cruise causes an emergency landing without consuming fuel")
    void cruiseWithFailedEngineTriggersEmergencyLanding() {
        bringAircraftToInFlight();

        // reset(): clears all stubbing and recorded interactions of these doubles.
        // After this, engine.isOperational() returns the default value (false),
        // which simulates the engine failing in mid-air, and verify() below only
        // sees what happened during cruise(), not during preparation/takeoff.
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

    // ---------------------------------------------------------------------
    // 7. doThrow() on a void method inside cruise
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("a fuel consumption failure triggers an emergency landing and a FlightException")
    void cruiseFuelFailureTriggersEmergencyLanding() {
        bringAircraftToInFlight();
        IllegalStateException fuelFailure = new IllegalStateException("Not enough fuel.");
        doThrow(fuelFailure).when(fuelSystem).consumeFuel(anyDouble());

        FlightException exception =
                assertThrows(FlightException.class, () -> aircraft.cruise(5_000.0));

        assertEquals("A problem occurred while consuming fuel.", exception.getMessage());
        assertSame(fuelFailure, exception.getCause());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(5_000.0);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    // ---------------------------------------------------------------------
    // 8. Normal landing and lifecycle rules
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("land deploys the landing gear, stops the engine and the aircraft can fly again")
    void landDeploysGearStopsEngineAndAllowsAnotherFlight() {
        bringAircraftToInFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        InOrder order = inOrder(landingGear, engine);
        order.verify(landingGear).deploy();
        order.verify(engine).stop();

        // A LANDED aircraft may prepare for another flight; the checks run a second time.
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
