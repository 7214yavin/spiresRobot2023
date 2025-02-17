package frc.hardwareWrappers.MotorCtrl.Sim;

import java.util.ArrayList;

import edu.wpi.first.math.util.Units;
import frc.Constants;
import frc.hardwareWrappers.SimDeviceBanks;
import frc.hardwareWrappers.MotorCtrl.AbstractSimmableMotorController;
import frc.lib.Signal.Annotations.Signal;

/**
 * CTRE doesn't currently support simulating the internal functionality of a TalonFX.
 * We provide a wrapper and interface abstraction here to enable simulation through
 * a transparent wrapper layer in robot code.
 */
public class SimSmartMotor extends AbstractSimmableMotorController {

    public ArrayList<SimSmartMotor> simFollowers = new ArrayList<SimSmartMotor>();

    boolean isInverted;
    double kP;
    double kI;
    double kD;

    @Signal(units="V")
    private double curWindingVoltage;
    @Signal(units="A")
    private double curCurrent;
    @Signal(units="radpersec")
    private double curVel_radpersec;
    @Signal(units="V")
    private double curSupplyVoltage = 12.0;
    @Signal(units="rad")
    private double curPos_rad;

    public SimSmartMotor(int can_id){
        SimDeviceBanks.addCANDevice(this, can_id);
    }

    public void setCurrent_A(int currentLimit) {
  
    }


    @Override
    public void setInverted(boolean invert) {
        isInverted = invert;
    }

    
    @Override
    public void setSoftLimits(float fwd, float rev, boolean e) {
    }



    @Override
    public void setClosedLoopGains(double p, double i, double d) {
        kP = p;
        kI = i;
        kD = d;
    }


    @Override
    public void setClosedLoopCmd(double velocityCmd_radpersec, double arbFF_V) {
        setVoltageCmd(pidSim(velocityCmd_radpersec, arbFF_V));
    }


    @Override
    public void setVoltageCmd(double cmd_v) {
        curWindingVoltage = limitVoltage(cmd_v) * (isInverted?-1.0:1.0);
        for(AbstractSimmableMotorController follower : simFollowers){
            follower.setVoltageCmd(curWindingVoltage);
        }
    }


    // Returns the current draw of the motor controller from the rest of the electrical system.
    @Override
    public double getCurrent_A() {
        return curCurrent;
    }

    @Override
    public double getVelocity_radpersec() {
        return curVel_radpersec * (isInverted?-1.0:1.0);
    }

    @Override
    public double getPosition_rad() {
        return curPos_rad * (isInverted?-1.0:1.0);
    }

    public void sim_setActualVelocity(double velocity_radpersec){
        curVel_radpersec = velocity_radpersec;
        curPos_rad += curVel_radpersec * Constants.SIM_SAMPLE_RATE_SEC;
    }

    public void sim_setActualPosition(double pos_rad){
        curVel_radpersec = (pos_rad - curPos_rad) / Constants.SIM_SAMPLE_RATE_SEC;
        curPos_rad = pos_rad;
    }

    public double sim_getWindingVoltage(){
        return curWindingVoltage * curLimitFactor;
    }

    public void sim_setSupplyVoltage(double supply_V){
        curSupplyVoltage = supply_V;
    }

    // Set the current flowing through the motor windings
    public void sim_setCurrent(double cur_A){
        curCurrent = cur_A * Math.signum(curSupplyVoltage); //H bridge will reverse current flow
    }

    private double limitVoltage(double in){
        if(in > curSupplyVoltage){
            return curSupplyVoltage;
        } else if (in < -curSupplyVoltage){
            return -curSupplyVoltage;
        } else {
            return in;
        }
    }

    @Signal
    double curLimitFactor = 1.0;
    final double CURRENT_LIM_I_GAIN = 0.02;
    public void sim_updateCurrentLimit(){
        // whelp. Super rough aproximation of a current limit. Just an I gain on 
        // whether or not we're above the current limit. Should be updated faster
        // at the sim ts rate

        double err = 40.0 - Math.abs(curCurrent);
        curLimitFactor += Constants.SIM_SAMPLE_RATE_SEC*CURRENT_LIM_I_GAIN*err;

        if(curLimitFactor > 1.0){
            curLimitFactor = 1.0;
        }

        if(curLimitFactor < 0.0){
            curLimitFactor = 0.0;
        }

    }


    double velErr_accum;
    double velErr_prev;
    /**
     * A rough guess at the behavior of the closed loop controllers on the smart motor controllers
     */
    private double pidSim(double vel_cmd, double arb_ff_V){

        var velError_RPM = Units.radiansPerSecondToRotationsPerMinute(vel_cmd - getVelocity_radpersec());

        // Simulate sensor dropout... or just the whole "close enough to zero" behavior
        if(Math.abs(velError_RPM) < 5.0){
            velError_RPM = 0;
        }
        
        velErr_accum += velError_RPM;
        
        var velErr_delta = (velError_RPM - velErr_prev)/Constants.Ts;

        var pTerm = velError_RPM * kP;
        var dTerm = velErr_delta * kD;
        var iTerm = velErr_accum * kI;

        velErr_prev = velError_RPM;

        return limitVoltage((pTerm + dTerm + iTerm) * curSupplyVoltage + arb_ff_V);
    }


    @Override
    public void follow(Object leader) {
        if(leader.getClass() == SimSmartMotor.class){
            ((SimSmartMotor)leader).simFollowers.add(this);
        } else {
            throw new IllegalArgumentException(leader.getClass().toString() + " cannot be followed by a " + this.getClass().toString());
        }

    }

    @Override
    public double getAppliedVoltage_V() {
        return sim_getWindingVoltage();
    }

    @Override
    public void resetDistance() {
        curPos_rad = 0;
    }


    
}
