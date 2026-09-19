package be.ucll.itintegrationproject.NL_14_backend.repository;

import be.ucll.itintegrationproject.NL_14_backend.model.ControlInputLogDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ControlInputLogMongoRepository
    extends MongoRepository<ControlInputLogDoc, String> {}
