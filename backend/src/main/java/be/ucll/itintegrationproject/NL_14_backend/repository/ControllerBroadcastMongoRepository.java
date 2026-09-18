package be.ucll.itintegrationproject.NL_14_backend.repository;

import be.ucll.itintegrationproject.NL_14_backend.model.ControllerBroadcastDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ControllerBroadcastMongoRepository
    extends MongoRepository<ControllerBroadcastDoc, String> {}
