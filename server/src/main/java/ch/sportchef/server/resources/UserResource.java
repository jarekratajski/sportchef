package ch.sportchef.server.resources;

import ch.sportchef.server.App;
import ch.sportchef.server.representations.User;
import ch.sportchef.server.services.UserService;

import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    private final UserService userService;

    public UserResource() {
        this.userService = App.getService(UserService.class);
    }

    @GET
    @Path("/{id}")
    public Response readUser(@PathParam("id") final long id) {
        final Optional<User> user = userService.readUserById(id);
        if (user.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(user).build();
    }

    @POST
    public Response createUser(@Valid User user) throws URISyntaxException {
        final User newUser = userService.storeUser(user);
        return Response.created(new URI(String.valueOf(newUser.getId()))).build();
    }
}
