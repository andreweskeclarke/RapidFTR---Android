package com.rapidftr.model;

import android.database.Cursor;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.rapidftr.RapidFtrApplication;
import com.rapidftr.database.Database;
import com.rapidftr.repository.ChildRepository;
import com.rapidftr.repository.EnquiryRepository;
import com.rapidftr.repository.PotentialMatchRepository;
import com.rapidftr.utils.RapidFtrDateTime;
import lombok.Cleanup;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.rapidftr.database.Database.EnquiryTableColumn.content;
import static com.rapidftr.utils.JSONArrays.asList;

public class Enquiry extends BaseModel {

    public static final String ENQUIRY_FORM_NAME = "Enquiries";
    public static final String FIELD_ATTACHMENTS = "_attachments";

    public Enquiry() throws JSONException {
        super();
        this.setUniqueId(createUniqueId());
    }

    public Enquiry(String content, String createdBy) throws JSONException {
        super(content);
        this.setCreatedBy(createdBy);
        this.setLastUpdatedAt(RapidFtrDateTime.now().defaultFormat());
    }

    public Enquiry(Cursor cursor) throws JSONException {
        super(cursor.getString(cursor.getColumnIndex(content.getColumnName())));

        for (Database.EnquiryTableColumn column : Database.EnquiryTableColumn.values()) {
            final int columnIndex = cursor.getColumnIndex(column.getColumnName());

            if (columnIndex < 0 || column.equals(content)) {
                continue;
            } else if (column.getPrimitiveType().equals(Boolean.class)) {
                this.put(column.getColumnName(), cursor.getInt(columnIndex) == 1);
            } else {
                this.put(column.getColumnName(), cursor.getString(columnIndex));
            }
        }
    }

    public Enquiry(String enquiryJSON) throws JSONException {
        super(enquiryJSON);
        setHistories();
    }

    public boolean isValid() {
        int numberOfInternalFields = names().length();

        for (Database.EnquiryTableColumn field : Database.EnquiryTableColumn.internalFields()) {
            if (has(field.getColumnName())) {
                numberOfInternalFields--;
            }
        }
        return numberOfInternalFields > 0;
    }

    public JSONObject values() throws JSONException {
        List<Object> names = asList(names());

        Iterable<Object> systemFields = Iterables.transform(Database.EnquiryTableColumn.systemFields(), new Function<Database.EnquiryTableColumn, Object>() {
            @Override
            public Object apply(Database.EnquiryTableColumn enquiryTableColumn) {
                return enquiryTableColumn.getColumnName();
            }
        });

        Iterables.removeAll(names, Lists.newArrayList(systemFields));
        return new JSONObject(this, names.toArray(new String[names.size()]));
    }

    @Override
    public List<BaseModel> getConfirmedMatchingModels() {

        @Cleanup PotentialMatchRepository potentialMatchRepository = RapidFtrApplication.getApplicationInstance().getBean(PotentialMatchRepository.class);
        @Cleanup ChildRepository childRepository = RapidFtrApplication.getApplicationInstance().getBean(ChildRepository.class);
        @Cleanup EnquiryRepository enquiryRepository = RapidFtrApplication.getApplicationInstance().getBean(EnquiryRepository.class);

        return getMatchesByConfirmationStatus(potentialMatchRepository, childRepository, true);
    }

    @Override
    public List<BaseModel> getPotentialMatchingModels() throws JSONException {

        @Cleanup PotentialMatchRepository potentialMatchRepository = RapidFtrApplication.getApplicationInstance().getBean(PotentialMatchRepository.class);
        @Cleanup ChildRepository childRepository = RapidFtrApplication.getApplicationInstance().getBean(ChildRepository.class);
        @Cleanup EnquiryRepository enquiryRepository = RapidFtrApplication.getApplicationInstance().getBean(EnquiryRepository.class);

        return getMatchesByConfirmationStatus(potentialMatchRepository, childRepository, false);
    }

    private List<BaseModel> getMatchesByConfirmationStatus(PotentialMatchRepository potentialMatchRepo, ChildRepository childRepository, boolean status) {
        List<BaseModel> models = new ArrayList<BaseModel>();
        try {
            List<PotentialMatch> matches = potentialMatchRepo.getPotentialMatchesFor(this);
            Collection<PotentialMatch> potentialMatches = Collections2.filter(matches, new PotentialMatch.FilterByConfirmationStatus(status));
            models.addAll(childRepository.getAllWithInternalIds(idsFromMatches(potentialMatches)));
            return models;
        } catch (JSONException exception) {
            return new ArrayList<BaseModel>();
        }
    }

    public static List<String> idsFromMatches(Collection<PotentialMatch> potentialMatches) {
        List<String> ids = new ArrayList<String>();
        for (PotentialMatch potentialMatch : potentialMatches) {
            ids.add(potentialMatch.getChildId());
        }
        return ids;
    }

    public String getInternalId() {
        return getString(Database.EnquiryTableColumn.internal_id.getColumnName());
    }

    @Override
    public String getApiPath() {
        return "/api/enquiries";
    }

    @Override
    public String getApiParameter() {
        return "enquiry";
    }
}
