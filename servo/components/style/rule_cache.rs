/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

//! A cache from rule node to computed values, in order to cache reset
//! properties.

use crate::logical_geometry::WritingMode;
use crate::properties::{ComputedValues, StyleBuilder};
use crate::rule_tree::StrongRuleNode;
use crate::selector_parser::PseudoElement;
use crate::shared_lock::StylesheetGuards;
use crate::values::computed::{NonNegativeLength, Zoom};
use crate::values::specified::color::ColorSchemeFlags;
use fxhash::FxHashMap;
use servo_arc::Arc;
use smallvec::SmallVec;

/// The conditions for caching and matching a style in the rule cache.
#[derive(Clone, Debug, Default)]
pub struct RuleCacheConditions {
    uncacheable: bool,
    font_size: Option<NonNegativeLength>,
    line_height: Option<NonNegativeLength>,
    writing_mode: Option<WritingMode>,
    color_scheme: Option<ColorSchemeFlags>,
}

impl RuleCacheConditions {
    /// Sets the style as depending in the font-size value.
    pub fn set_font_size_dependency(&mut self, font_size: NonNegativeLength) {
        debug_assert!(self.font_size.map_or(true, |f| f == font_size));
        self.font_size = Some(font_size);
    }

    /// Sets the style as depending in the line-height value.
    pub fn set_line_height_dependency(&mut self, line_height: NonNegativeLength) {
        debug_assert!(self.line_height.map_or(true, |l| l == line_height));
        self.line_height = Some(line_height);
    }

    /// Sets the style as depending in the color-scheme property value.
    pub fn set_color_scheme_dependency(&mut self, color_scheme: ColorSchemeFlags) {
        debug_assert!(self.color_scheme.map_or(true, |cs| cs == color_scheme));
        self.color_scheme = Some(color_scheme);
    }

    /// Sets the style as uncacheable.
    pub fn set_uncacheable(&mut self) {
        self.uncacheable = true;
    }

    /// Sets the style as depending in the writing-mode value `writing_mode`.
    pub fn set_writing_mode_dependency(&mut self, writing_mode: WritingMode) {
        debug_assert!(self.writing_mode.map_or(true, |wm| wm == writing_mode));
        self.writing_mode = Some(writing_mode);
    }

    /// Returns whether the current style's reset properties are cacheable.
    fn cacheable(&self) -> bool {
        !self.uncacheable
    }
}

#[derive(Debug)]
struct CachedConditions {
    font_size: Option<NonNegativeLength>,
    line_height: Option<NonNegativeLength>,
    color_scheme: Option<ColorSchemeFlags>,
    writing_mode: Option<WritingMode>,
    zoom: Zoom,
}

impl CachedConditions {
    /// Returns whether `style` matches the conditions.
    fn matches(&self, style: &StyleBuilder) -> bool {
        if style.effective_zoom != self.zoom {
            return false;
        }

        if let Some(fs) = self.font_size {
            if style.get_font().clone_font_size().computed_size != fs {
                return false;
            }
        }

        if let Some(lh) = self.line_height {
            let new_line_height =
                style
                    .device
                    .calc_line_height(&style.get_font(), style.writing_mode, None);
            if new_line_height != lh {
                return false;
            }
        }

        if let Some(cs) = self.color_scheme {
            if style.get_inherited_ui().clone_color_scheme().bits != cs {
                return false;
            }
        }

        if let Some(wm) = self.writing_mode {
            if style.writing_mode != wm {
                return false;
            }
        }

        true
    }
}

/// A TLS cache from rules matched to computed values.
pub struct RuleCache {
    // FIXME(emilio): Consider using LRUCache or something like that?
    map: FxHashMap<StrongRuleNode, SmallVec<[(CachedConditions, Arc<ComputedValues>); 1]>>,
}

impl RuleCache {
    /// Creates an empty `RuleCache`.
    pub fn new() -> Self {
        Self {
            map: FxHashMap::default(),
        }
    }

    /// Walk the rule tree and return a rule node for using as the key
    /// for rule cache.
    ///
    /// It currently skips a rule node when it is neither from a style
    /// rule, nor containing any declaration of reset property. We don't
    /// skip style rule so that we don't need to walk a long way in the
    /// worst case. Skipping declarations rule nodes should be enough
    /// to address common cases that rule cache would fail to share
    /// when using the rule node directly, like preshint, style attrs,
    /// and animations.
    fn get_rule_node_for_cache<'r>(
        guards: &StylesheetGuards,
        mut rule_node: Option<&'r StrongRuleNode>,
    ) -> Option<&'r StrongRuleNode> {
        while let Some(node) = rule_node {
            match node.style_source() {
                Some(s) => match s.as_declarations() {
                    Some(decls) => {
                        let cascade_level = node.cascade_level();
                        let decls = decls.read_with(cascade_level.guard(guards));
                        if decls.contains_any_reset() {
                            break;
                        }
                    },
                    None => break,
                },
                None => {},
            }
            rule_node = node.parent();
        }
        rule_node
    }

    /// Finds a node in the properties matched cache.
    ///
    /// This needs to receive a `StyleBuilder` with the `early` properties
    /// already applied.
    pub fn find(
        &self,
        guards: &StylesheetGuards,
        builder_with_early_props: &StyleBuilder,
    ) -> Option<&ComputedValues> {
        // A pseudo-element with property restrictions can result in different
        // computed values if it's also used for a non-pseudo.
        if builder_with_early_props
            .pseudo
            .and_then(|p| p.property_restriction())
            .is_some()
        {
            return None;
        }

        let rules = builder_with_early_props.rules.as_ref();
        let rules = Self::get_rule_node_for_cache(guards, rules)?;
        let cached_values = self.map.get(rules)?;

        for &(ref conditions, ref values) in cached_values.iter() {
            if conditions.matches(builder_with_early_props) {
                debug!("Using cached reset style with conditions {:?}", conditions);
                return Some(&**values);
            }
        }
        None
    }

    /// Inserts a node into the rules cache if possible.
    ///
    /// Returns whether the style was inserted into the cache.
    pub fn insert_if_possible(
        &mut self,
        guards: &StylesheetGuards,
        style: &Arc<ComputedValues>,
        pseudo: Option<&PseudoElement>,
        conditions: &RuleCacheConditions,
    ) -> bool {
        if !conditions.cacheable() {
            return false;
        }

        // A pseudo-element with property restrictions can result in different
        // computed values if it's also used for a non-pseudo.
        if pseudo.and_then(|p| p.property_restriction()).is_some() {
            return false;
        }

        let rules = style.rules.as_ref();
        let rules = match Self::get_rule_node_for_cache(guards, rules) {
            Some(r) => r.clone(),
            None => return false,
        };

        debug!(
            "Inserting cached reset style with conditions {:?}",
            conditions
        );
        let cached_conditions = CachedConditions {
            writing_mode: conditions.writing_mode,
            font_size: conditions.font_size,
            line_height: conditions.line_height,
            color_scheme: conditions.color_scheme,
            zoom: style.effective_zoom,
        };
        self.map
            .entry(rules)
            .or_default()
            .push((cached_conditions, style.clone()));
        true
    }
}
