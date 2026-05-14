package com.tsadmin.transport.common.enums;

public enum VehicleStatus
{
    /** 空闲 */
    AVAILABLE,
    /** 接单行驶 */
    ORDER_TAKEN,
    /** 装货 */
    LOADING,
    /** 运货行驶 */
    TRANSPORTING,
    /** 卸货 */
    UNLOADING,
    /** 停车等待 */
    FREEZE
}
