package pl.com.bottega.factory.shortages.prediction.monitoring;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import pl.com.bottega.factory.product.management.RefNoId;
import pl.com.bottega.factory.shortages.prediction.Configuration;
import pl.com.bottega.factory.shortages.prediction.calculation.ShortageForecasts;
import pl.com.bottega.factory.shortages.prediction.monitoring.persistence.ShortagesDao;
import pl.com.bottega.factory.shortages.prediction.monitoring.persistence.ShortagesEntity;
import pl.com.bottega.tools.TechnicalId;

import java.util.Optional;

@Component
@AllArgsConstructor
class ShortagePredictionProcessORMRepository {

    private final ShortagesDao dao;
    private final ShortageDiffPolicy policy = ShortageDiffPolicy.ValuesAreNotSame;
    private final ShortageForecasts forecasts;
    private final Configuration configuration = () -> 14;
    private final ShortageEvents events;

    ShortagePredictionProcess get(RefNoId refNo) {
        Optional<ShortagesEntity> entity = dao.findByRefNo(refNo.getRefNo());
        return new ShortagePredictionProcess(
                entity.map(ShortagesEntity::createId)
                        .orElseGet(() -> ShortagesEntity.createId(refNo)),
                entity.map(ShortagesEntity::getShortage).orElse(null),
                policy, forecasts, configuration, new EventsHandler()
        );
    }

    void save(ShortagePredictionProcess model) {
        // persisted after event
    }

    private void save(NewShortage event) {
        RefNoId refNo = event.getRefNo();
        ShortagesEntity entity = TechnicalId.findOrDefault(
                refNo, dao::findOne,
                () -> dao.save(new ShortagesEntity(refNo.getRefNo())));
        entity.setShortage(event.getShortage());
        events.emit(event);
    }

    private void delete(ShortageSolved event) {
        dao.delete(TechnicalId.get(event.getRefNo()));
        events.emit(event);
    }

    private class EventsHandler implements ShortageEvents {
        @Override
        public void emit(NewShortage event) {
            save(event);
        }

        @Override
        public void emit(ShortageSolved event) {
            delete(event);
        }
    }
}
