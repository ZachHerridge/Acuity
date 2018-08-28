package com.acuitybotting.statistics.services;

import com.acuitybotting.common.utils.GsonUtil;
import com.acuitybotting.data.flow.messaging.services.events.MessageEvent;
import com.acuitybotting.data.flow.messaging.services.identity.RabbitUtil;
import com.acuitybotting.db.arango.acuity.rabbit_db.domain.RabbitDocumentBase;
import com.acuitybotting.db.arango.acuity.rabbit_db.domain.RabbitSubDocument;
import com.acuitybotting.db.arango.acuity.rabbit_db.domain.gson.GsonRabbitDocument;
import com.acuitybotting.db.arangodb.repositories.resources.accounts.RsAccountInfo;
import com.acuitybotting.db.arango.acuity.rabbit_db.service.RabbitDbService;
import com.acuitybotting.db.arango.acuity.statistic.event.domain.StatisticEvent;
import com.acuitybotting.db.arango.acuity.statistic.event.repository.StatisticEventRepository;
import com.acuitybotting.db.influx.InfluxDbService;
import com.acuitybotting.db.influx.domain.write.Point;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Created by Zachary Herridge on 8/22/2018.
 */
@Service
public class AccountStatisticsService implements CommandLineRunner {

    private final StatisticEventRepository eventRepository;
    private final InfluxDbService influxDbService;
    private final RabbitDbService rabbitDbService;
    private final RsBuddyService rsBuddyService;

    public AccountStatisticsService(StatisticEventRepository eventRepository, InfluxDbService influxDbService, RabbitDbService rabbitDbService, RsBuddyService rsBuddyService) {
        this.eventRepository = eventRepository;
        this.influxDbService = influxDbService;
        this.rabbitDbService = rabbitDbService;
        this.rsBuddyService = rsBuddyService;
    }

    @EventListener
    public void onMessage(MessageEvent messageEvent) {

        if (messageEvent.getRouting().endsWith("rabbitdb.update.services.rs-accounts.players")){
            Gson gson = new Gson();

            DbUpdate update = gson.fromJson(messageEvent.getMessage().getBody(), DbUpdate.class);
            if (update.getPrevious() == null || update.getCurrent() == null) return;


            RsAccountDocument previous = update.getPrevious().getSubDocumentAs(RsAccountDocument.class);
            RsAccountDocument current = update.getCurrent().getSubDocumentAs(RsAccountDocument.class);

            if (previous.getState().getBank() == null) return;

            long previousValue = value(previous);
            long currentValue = value(current);

            long difference = currentValue - previousValue;


            if (difference == 0) return;

            Point point = new Point();
            point.setMeasurement("value-change");
            point.getFields().put("change", difference);
            point.getTags().put("email", previous.getParent().getSubKey());
            point.getTags().put("principalId", previous.getParent().getPrincipalId());
            influxDbService.write("rs-account-stats", point);
        }

        String eventType = messageEvent.getMessage().getAttributes().get("eventType");
        if ("account.banned".equals(eventType) || "account.locked".equals(eventType)) {
            JsonObject body = new Gson().fromJson(messageEvent.getMessage().getBody(), JsonObject.class);
            String accountLogin = body.get("accountLogin").getAsString();
            if (eventRepository.existsByTypeAndKey(eventType, accountLogin)) return;

            StatisticEvent event = new StatisticEvent();
            event.setType(eventType);
            event.setKey(accountLogin);
            eventRepository.save(event);

            Point point = new Point();
            point.setMeasurement(event.getType());
            point.getTags().put("principalId", RabbitUtil.routeToUserId(messageEvent.getRouting()));
            point.getTags().put("clientName", GsonUtil.getOrDefault(body.get("clientName"), "rspeer"));
            point.getFields().put("count", 1);
            influxDbService.write("rs-account-stats", point);
        }
    }


    private long value(RsAccountDocument document){
        long accountValue = 0;
        accountValue += rsBuddyService.value(document.getState().getBank());
        accountValue += rsBuddyService.value(document.getState().getInventory());
        accountValue += rsBuddyService.value(document.getState().getEquipment());
        return accountValue;
    }

    @Scheduled(initialDelay = 0, fixedRate = 15000)
    public void test() {
        String query = "FOR r in @@collection\n" +
                "FILTER r.database == 'services.rs-accounts'\n" +
                "RETURN r";

        Set<RsAccountDocument> all = rabbitDbService.query(query).findAll(RsAccountDocument.class);
        long bannedValue = 0;
        long activeValue = 0;

        for (RsAccountDocument document : all) {
            if (document.getState() == null) continue;
            long value = value(document);
            if (document.isBanned()) bannedValue += value;
            else activeValue += value;
        }

        Point point = new Point();
        point.setMeasurement("overall-value");
        point.getFields().put("total", bannedValue + activeValue);
        point.getFields().put("banned", bannedValue);
        point.getFields().put("active", activeValue);
        influxDbService.write("rs-account-stats", point);
    }

    @Override
    public void run(String... args) throws Exception {
    }

    @Getter
    public static class DbUpdate{

        private GsonRabbitDocument previous;
        private GsonRabbitDocument current;

    }

    @Getter
    public static class RsAccountDocument extends RabbitSubDocument {

        private boolean banned;
        private RsAccountInfo state;

    }
}
