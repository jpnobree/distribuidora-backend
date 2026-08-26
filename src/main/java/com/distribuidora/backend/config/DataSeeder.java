package com.distribuidora.backend.config;

import com.distribuidora.backend.model.Category;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.CategoryRepository;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

// Cria, na primeira execucao, os usuarios padrao (admin/cliente), as
// categorias e alguns produtos de exemplo -- espelhando o catalogo que ja
// existia no front-end (src/data/*.js) so para o site nao nascer vazio.
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminUsername;
    private final String adminPassword;
    private final String demoUsername;
    private final String demoPassword;

    public DataSeeder(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-username}") String adminUsername,
            @Value("${app.seed.admin-password}") String adminPassword,
            @Value("${app.seed.demo-username}") String demoUsername,
            @Value("${app.seed.demo-password}") String demoPassword) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedCategories();
        seedProducts();
    }

    private void seedUsers() {
        if (!userRepository.existsByUsername(adminUsername)) {
            userRepository.save(new User(adminUsername, passwordEncoder.encode(adminPassword), null, Role.ADMIN));
        }
        if (!userRepository.existsByUsername(demoUsername)) {
            userRepository.save(new User(demoUsername, passwordEncoder.encode(demoPassword), null, Role.USER));
        }
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }
        List<Category> categories = List.of(
                new Category("carnes-aves", "Carnes & Aves", "🥩"),
                new Category("laticinios-frios", "Laticínios & Frios", "🧀"),
                new Category("hortifruti", "Hortifruti", "🥬"),
                new Category("mercearia", "Mercearia", "🛒"),
                new Category("bebidas", "Bebidas", "🥤"),
                new Category("congelados", "Congelados", "❄️"),
                new Category("padaria", "Padaria & Confeitaria", "🥖"),
                new Category("descartaveis-limpeza", "Descartáveis & Limpeza", "🧴"));
        categoryRepository.saveAll(categories);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            return;
        }

        List<Product> products = List.of(
                product("picanha-premium-98562", "98562", "Picanha Premium", "carnes-aves", "kg",
                        79.9, List.of("Premium", "Resfriado"),
                        "Picanha selecionada, com capa de gordura uniforme, ideal para churrasco.", "Brasil", true),
                product("file-de-frango-11023", "11023", "Filé de Peito de Frango", "carnes-aves", "kg",
                        18.5, List.of("Resfriado"),
                        "Filé de peito sem osso e sem pele, embalado a vácuo.", "Brasil", true),
                product("queijo-mussarela-40011", "40011", "Queijo Mussarela em Barra", "laticinios-frios", "kg",
                        34.2, List.of(),
                        "Mussarela fatiável, ótimo derretimento, ideal para lanches e pizzas.", "Brasil", true),
                product("tomate-italiano-50011", "50011", "Tomate Italiano", "hortifruti", "kg",
                        7.9, List.of(),
                        "Tomate selecionado, ideal para molhos e saladas.", "Brasil", true),
                product("arroz-branco-60011", "60011", "Arroz Branco Tipo 1", "mercearia", "saco 5kg",
                        26.9, List.of(),
                        "Arroz tipo 1, grãos longos e soltos.", "Brasil", true),
                product("agua-mineral-70011", "70011", "Água Mineral sem Gás", "bebidas", "fardo 12x500ml",
                        14.9, List.of(),
                        "Água mineral natural, fardo fechado.", "Brasil", true));

        productRepository.saveAll(products);
    }

    private Product product(String slug, String sku, String name, String category, String unit,
                             Double price, List<String> tags, String description, String origin, boolean available) {
        Product product = new Product();
        product.setSlug(slug);
        product.setSku(sku);
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unit);
        product.setPrice(price);
        product.setTags(tags);
        product.setDescription(description);
        product.setOrigin(origin);
        product.setAvailable(available);
        return product;
    }
}
