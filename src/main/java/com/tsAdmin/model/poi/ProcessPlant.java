package com.tsAdmin.model.poi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tsAdmin.common.Coordinate;
import com.tsAdmin.model.ProductType;

public class ProcessPlant extends Purchaser implements Dumper
{
    private static final Logger logger = LogManager.getLogger(ProcessPlant.class);

    private static double PROCESSING_LOSS, PROCESSING_SPEED;
    private double rawMaterial;

    public static void setProcessingLoss(int loss) { PROCESSING_LOSS = loss / 100.0; }
    public static void setProcessingSpeed(int speed) { PROCESSING_SPEED = speed / 100.0; }

    public ProcessPlant(String uuid, String name, ProductType productType, Coordinate position, int maxStock)
    {
        super(uuid, name, productType, position, maxStock);
    }

    public void setRawMaterial(double processing) { this.rawMaterial = processing; }
    public void addProcessing(double toProcess) { rawMaterial += toProcess; }

    @Override
    public void update()
    {
        // 模拟处理待处理货物
        double completed = Math.min(rawMaterial, PROCESSING_SPEED * maxStock);

        rawMaterial -= completed;
        stock += completed * (1 - PROCESSING_LOSS);
        if (stock > maxStock)
        {
            logger.warn("Stock(value:{}) of POI(UUID:{}) overflowed maximum stock(value:{})", stock, uuid, maxStock);
        }

        // 尝试根据库存生成订单
        tryGenerateDemand(stock + rawMaterial * PROCESSING_LOSS);
    }

    @Override
    public void onDemandCompleted()
    {
        rawMaterial += demand.getQuantity();
        demand = null;
    }

    @Override
    public double getStock() { return stock; }
}
