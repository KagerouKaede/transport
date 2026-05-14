package com.tsadmin.transport.service.simulation.domain.poi;

import java.util.UUID;

import jakarta.persistence.Entity;

@Entity
public class ProcessPlant extends Purchaser implements Dumper
{
    // private static double PROCESSING_LOSS, PROCESSING_SPEED;
    // private double unprocessedStock;

    // public static void setProcessingLoss(int loss) { PROCESSING_LOSS = loss / 100.0; }
    // public static void setProcessingSpeed(int speed) { PROCESSING_SPEED = speed / 100.0; }

    protected ProcessPlant() {}
    public ProcessPlant(UUID sandboxId, UUID poiId, int maxStock)
    {
        super(sandboxId, poiId, maxStock);
    }

    // public void setUnprocessedStock(double processing) { this.unprocessedStock = processing; }
    // public void addProcessing(double toProcess) { unprocessedStock += toProcess; }

    // @Override
    // public void update()
    // {
    //     // 模拟处理待处理货物
    //     double completed = Math.min(unprocessedStock, PROCESSING_SPEED * maxStock);

    //     unprocessedStock -= completed;
    //     stock += completed * (1 - PROCESSING_LOSS);

    //     // 尝试根据库存生成订单
    //     tryGenerateDemand(stock + unprocessedStock * PROCESSING_LOSS);
    // }

    // @Override
    // public void onDemandCompleted()
    // {
    //     unprocessedStock += demand.getQuantity();
    //     demand = null;
    // }
}
