import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.util.ArrayList;
import java.util.List;

public class Category {
    private String title;

    @JsonManagedReference
    private List<Recipe> recipeList;

    Category(String title){
        this.recipeList = new ArrayList<>();
        this.title = title;
    }

    public void addCorrespondingRecipe(Recipe recipe){
        recipeList.add(recipe);
    }
}
