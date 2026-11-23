package app.auth;

import app.storage.InMemoryUserRepository;
import app.storage.UserRepository;

class InMemoryUserRepositoryTest extends UserRepositoryContract {

    @Override
    UserRepository repository() {
        return new InMemoryUserRepository();
    }
}
